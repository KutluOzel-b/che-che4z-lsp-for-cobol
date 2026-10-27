/*
 * Copyright (c) 2026 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.service.delegates.formations.layout;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Re-lays out COBOL source code: places division, section and paragraph headers into Area A,
 * indents statements by their nesting, aligns data description clauses, normalizes spacing and
 * quotes.
 *
 * <p>The formatter never changes the meaning of the program. Lines it cannot safely re-render,
 * like continued literals, compiler directives, embedded EXEC blocks or COPY statements, are kept
 * or only moved horizontally. A line which would not fit into the content area anymore keeps its
 * original content.
 *
 * <p>An instance keeps state for a single run, create a new one for every document.
 */
public final class CobolLayoutFormatter {
  private static final int SEQUENCE_LENGTH = 6;
  private static final int INDICATOR = 6;
  private static final int CONTENT_START = 7;
  private static final int CONTENT_END = 72;
  private static final int FIXED_AREA_B = 4;
  private static final int FREE_WIDTH = 255;
  private static final String BLANK_SEQUENCE = StringUtils.repeat(' ', SEQUENCE_LENGTH);
  private static final Set<String> SELECTIONS = ImmutableSet.of("EVALUATE", "SEARCH");

  private enum Division {
    NONE,
    IDENTIFICATION,
    ENVIRONMENT,
    DATA,
    PROCEDURE
  }

  /** An open statement scope in the procedure division. */
  @AllArgsConstructor
  private static final class Frame {
    private final String kind;
    private final int open;
    private int body;
    /** A statement which may get a conditional phrase but has not got any yet. */
    private boolean soft;
  }

  /** A recognized conditional phrase like NOT AT END. */
  @AllArgsConstructor
  private static final class Phrase {
    private final Frame owner;
    private final int length;
  }

  private final LayoutSettings settings;
  private final boolean fixed;
  private final int indent;
  private final int areaB;
  private final int headerContinuation;
  private final Renderer renderer;
  private final DataAligner aligner;

  private final List<SourceLine> lines = new ArrayList<>();
  private final List<Word> stream = new ArrayList<>();
  private final List<Integer> streamStart = new ArrayList<>();
  private final List<List<DataAligner.Entry>> groups = new ArrayList<>();

  private Division division = Division.NONE;
  private boolean headerOpen;
  private boolean inExec;
  private boolean inCopy;
  private boolean pseudoText;
  private int blockShift;
  private boolean selectOpen;

  private DataAligner.Entry entry;
  private final Deque<int[]> levels = new ArrayDeque<>();
  private int lastItemDepth;
  private List<DataAligner.Entry> group;
  private boolean independentGroup;

  private final Deque<Frame> frames = new ArrayDeque<>();
  private boolean sentenceOpen;
  private int anchor;

  /**
   * Create a formatter.
   *
   * @param settings formatting options
   */
  public CobolLayoutFormatter(LayoutSettings settings) {
    this.settings = settings;
    this.fixed = settings.getSourceFormat() == LayoutSettings.SourceFormat.FIXED;
    this.indent = settings.getIndentWidth();
    this.areaB = fixed ? FIXED_AREA_B : indent;
    this.headerContinuation = Math.max(areaB, indent);
    this.renderer = new Renderer(settings, fixed ? CONTENT_END - CONTENT_START : FREE_WIDTH);
    this.aligner = new DataAligner(settings, renderer, areaB, fixed);
  }

  /**
   * Format the source lines.
   *
   * @param source lines of the document
   * @return the formatted lines
   */
  public List<String> format(List<String> source) {
    parse(source);
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).getKind() == SourceLine.Kind.CODE) {
        analyze(i, lines.get(i));
      }
    }
    groups.forEach(aligner::align);
    periods();
    List<String> result = new ArrayList<>();
    lines.forEach(line -> render(line, result));
    return result;
  }

  // ---------------------------------------------------------------- parsing

  private void parse(List<String> source) {
    boolean codeSeen = false;
    boolean freezeNext = false;
    SourceLine previousCode = null;
    for (String text : source) {
      SourceLine line = split(text);
      lines.add(line);
      streamStart.add(stream.size());
      classify(line, codeSeen);
      if (line.getKind() != SourceLine.Kind.CODE) {
        continue;
      }
      codeSeen = true;
      if (fixed && line.getIndicator() == '-') {
        line.setFrozen(true);
        if (previousCode != null) {
          previousCode.setFrozen(true);
        }
      }
      if (freezeNext) {
        line.setFrozen(true);
        freezeNext = false;
      }
      if (isContinued(line)) {
        line.setFrozen(true);
        freezeNext = !fixed;
      }
      streamStart.set(streamStart.size() - 1, stream.size());
      stream.addAll(line.getWords());
      previousCode = line;
    }
  }

  private SourceLine split(String text) {
    if (!fixed) {
      return new SourceLine(text, "", ' ', text, "");
    }
    int length = text.length();
    return new SourceLine(
        text,
        text.substring(0, Math.min(SEQUENCE_LENGTH, length)),
        length > INDICATOR ? text.charAt(INDICATOR) : ' ',
        length > CONTENT_START ? text.substring(CONTENT_START, Math.min(CONTENT_END, length)) : "",
        length > CONTENT_END ? text.substring(CONTENT_END) : "");
  }

  private void classify(SourceLine line, boolean codeSeen) {
    char indicator = line.getIndicator();
    String trimmed = line.getContent().trim();
    if (fixed && (indicator == '*' || indicator == '/')) {
      line.setKind(SourceLine.Kind.COMMENT);
    } else if (fixed && indicator != ' ' && indicator != 'D' && indicator != 'd'
        && indicator != '-') {
      line.setKind(SourceLine.Kind.DIRECTIVE);
    } else if (!codeSeen && isProcessStatement(line)) {
      line.setKind(SourceLine.Kind.DIRECTIVE);
    } else if (trimmed.isEmpty()) {
      line.setKind(indicator == '-' ? SourceLine.Kind.DIRECTIVE : SourceLine.Kind.BLANK);
    } else if (trimmed.startsWith("*>")) {
      line.setKind(SourceLine.Kind.COMMENT);
    } else if (trimmed.startsWith(">>")) {
      line.setKind(SourceLine.Kind.DIRECTIVE);
    } else {
      LineLexer.Result lexed = LineLexer.lex(line.getContent());
      line.setWords(lexed.getWords());
      line.setComment(lexed.getComment());
      if (lexed.getWords().isEmpty()) {
        line.setKind(SourceLine.Kind.COMMENT);
      } else if (Keywords.LISTING_DIRECTIVES.contains(line.upper(0))) {
        line.setKind(SourceLine.Kind.DIRECTIVE);
      }
    }
  }

  private static boolean isProcessStatement(SourceLine line) {
    for (String text : new String[] {line.getOriginal(), line.getContent()}) {
      String upper = text.trim().toUpperCase(Locale.ROOT);
      if (upper.equals("CBL") || upper.startsWith("CBL ") || upper.equals("PROCESS")
          || upper.startsWith("PROCESS ")) {
        return true;
      }
    }
    return false;
  }

  /** Check whether the line is continued on the next line, inside a literal or a word. */
  private boolean isContinued(SourceLine line) {
    List<Word> words = line.getWords();
    if (words.isEmpty()) {
      return false;
    }
    Word last = words.get(words.size() - 1);
    return last.isUnterminated() || (!fixed && last.isLiteral() && last.getText().endsWith("-"));
  }

  // --------------------------------------------------------------- analysis

  private void analyze(int index, SourceLine line) {
    if (inExec || inCopy) {
      continueBlock(index, line);
      return;
    }
    String first = line.upper(0);
    String second = line.upper(1);
    if ("DIVISION".equals(second) && Keywords.DIVISIONS.contains(first)) {
      startDivision(first);
      header(line);
      headerOpen = !line.endsWithPeriod();
      return;
    }
    if ("END".equals(first) && Keywords.END_MARKERS.contains(second)) {
      startDivision("");
      header(line);
      return;
    }
    if (headerOpen) {
      tokens(line, headerContinuation, headerContinuation);
      headerOpen = !line.endsWithPeriod();
      return;
    }
    if (division == Division.NONE) {
      division = infer(line);
    }
    switch (division) {
      case IDENTIFICATION:
        identification(line);
        break;
      case ENVIRONMENT:
        environment(line);
        break;
      case DATA:
        data(line);
        break;
      default:
        procedure(index, line);
    }
  }

  private void startDivision(String name) {
    switch (name) {
      case "IDENTIFICATION":
      case "ID":
        division = Division.IDENTIFICATION;
        break;
      case "ENVIRONMENT":
        division = Division.ENVIRONMENT;
        break;
      case "DATA":
        division = Division.DATA;
        break;
      case "PROCEDURE":
        division = Division.PROCEDURE;
        break;
      default:
        division = Division.NONE;
    }
    selectOpen = false;
    entry = null;
    group = null;
    levels.clear();
    endSentence();
  }

  /** Guess the division of a source fragment without division headers, like a copybook. */
  private static Division infer(SourceLine line) {
    String first = line.upper(0);
    boolean section = "SECTION".equals(line.upper(1));
    if (Keywords.levelNumber(line.getWords().get(0)) > 0
        || Keywords.FILE_DESCRIPTIONS.contains(first)
        || (section && Keywords.DATA_SECTIONS.contains(first))) {
      return Division.DATA;
    }
    if ("SELECT".equals(first)
        || Keywords.ENV_PARAGRAPHS.contains(first)
        || (section && Keywords.ENV_SECTIONS.contains(first))) {
      return Division.ENVIRONMENT;
    }
    if (Keywords.ID_PARAGRAPHS.contains(first)) {
      return Division.IDENTIFICATION;
    }
    return Division.PROCEDURE;
  }

  private void identification(SourceLine line) {
    // Paragraphs like AUTHOR contain free text comment entries, so only the headers are moved.
    if (Keywords.ID_PARAGRAPHS.contains(line.upper(0))) {
      line.setHeader(true);
      shift(line, -line.originalIndent());
    }
  }

  private void environment(SourceLine line) {
    String first = line.upper(0);
    if ("SECTION".equals(line.upper(1)) || Keywords.ENV_PARAGRAPHS.contains(first)) {
      header(line);
      selectOpen = false;
      return;
    }
    if (isBlockStart(first)) {
      startBlock(line, areaB);
      return;
    }
    boolean select = "SELECT".equals(first);
    if (selectOpen && !select) {
      tokens(line, areaB + indent, areaB + indent);
    } else {
      tokens(line, areaB, select ? areaB + indent : areaB);
    }
    selectOpen = (select || selectOpen) && !line.endsWithPeriod();
  }

  private void data(SourceLine line) {
    String first = line.upper(0);
    if ("SECTION".equals(line.upper(1))) {
      header(line);
      entry = null;
      group = null;
      levels.clear();
      return;
    }
    if (entry != null) {
      entry.addContinuation(line);
      line.setMode(SourceLine.Mode.ENTRY);
      entry = line.endsWithPeriod() ? null : entry;
      return;
    }
    if (Keywords.FILE_DESCRIPTIONS.contains(first)) {
      levels.clear();
      group = null;
      List<DataAligner.Entry> description = new ArrayList<>();
      groups.add(description);
      startEntry(line, 0, description);
      return;
    }
    int level = Keywords.levelNumber(line.getWords().get(0));
    if (level > 0) {
      dataEntry(line, level);
      return;
    }
    if (isBlockStart(first)) {
      startBlock(line, areaB);
    }
  }

  private void dataEntry(SourceLine line, int level) {
    int depth;
    if (level == 1 || level == Keywords.LEVEL_INDEPENDENT || level == Keywords.LEVEL_CONSTANT) {
      levels.clear();
      depth = 0;
      if (level == 1) {
        levels.push(new int[] {level, depth});
      }
    } else if (level == Keywords.LEVEL_RENAMES) {
      depth = 0;
    } else if (level == Keywords.LEVEL_CONDITION) {
      depth = lastItemDepth + 1;
    } else {
      while (!levels.isEmpty() && levels.peek()[0] >= level) {
        levels.pop();
      }
      // A fragment starting with a subordinate level (a copybook) is nested into a record.
      depth = levels.isEmpty() ? 1 : levels.peek()[1] + 1;
      levels.push(new int[] {level, depth});
    }
    if (level != Keywords.LEVEL_CONDITION) {
      lastItemDepth = depth;
    }
    boolean independent =
        level == Keywords.LEVEL_INDEPENDENT || level == Keywords.LEVEL_CONSTANT;
    boolean keepsGroup = level == Keywords.LEVEL_CONDITION || level == Keywords.LEVEL_RENAMES;
    if (group == null || level == 1 || (!keepsGroup && independent != independentGroup)) {
      group = new ArrayList<>();
      groups.add(group);
      independentGroup = independent;
    }
    startEntry(line, depth, group);
  }

  private void startEntry(SourceLine line, int depth, List<DataAligner.Entry> target) {
    DataAligner.Entry created = new DataAligner.Entry(line, depth);
    target.add(created);
    line.setMode(SourceLine.Mode.ENTRY);
    entry = line.endsWithPeriod() ? null : created;
  }

  private void procedure(int index, SourceLine line) {
    line.setProcedure(true);
    List<Word> words = line.getWords();
    if (words.size() == 1 && words.get(0).isPunctuation() && words.get(0).endsWithPeriod()) {
      line.setPeriodOnly(true);
      tokens(line, areaB, areaB);
      endSentence();
      return;
    }
    int headerWords = procedureHeader(line);
    if (headerWords > 0) {
      endSentence();
      line.setHeader(true);
      procWords(index, line, headerWords, 0);
      finish(line, 0, headerContinuation);
      return;
    }
    boolean continuation = startsContinuation(line);
    int depth = procWords(index, line, 0, -1);
    int column = areaB + depth * indent;
    finish(line, column, continuation ? column : column + indent);
  }

  /** Number of words forming a section or a paragraph header, or 0 if it is not a header. */
  private int procedureHeader(SourceLine line) {
    List<Word> words = line.getWords();
    String first = line.upper(0);
    String second = line.upper(1);
    if ("DECLARATIVES".equals(first) && words.get(0).endsWithPeriod()) {
      return 1;
    }
    if ("END".equals(first) && "DECLARATIVES".equals(second)) {
      return 2;
    }
    boolean areaA = fixed ? line.originalIndent() < FIXED_AREA_B : !sentenceOpen;
    if (!areaA || words.get(0).isLiteral() || !Keywords.isUserWord(first)) {
      return 0;
    }
    if ("SECTION".equals(second) && words.get(0).getSeparator().isEmpty()) {
      for (int i = 1; i < words.size(); i++) {
        if (words.get(i).endsWithPeriod()) {
          return i + 1;
        }
      }
      return words.size();
    }
    return words.get(0).endsWithPeriod() ? 1 : 0;
  }

  /** Check whether the first word of the line continues the previous statement. */
  private boolean startsContinuation(SourceLine line) {
    List<Word> words = line.getWords();
    String first = line.upper(0);
    return !words.get(0).isPunctuation()
        && !Keywords.VERBS.contains(first)
        && !Keywords.SCOPE_TERMINATORS.containsKey(first)
        && !"ELSE".equals(first)
        && !"WHEN".equals(first)
        && !"NEXT".equals(first)
        && !isBlockStart(first)
        && phrase(words, 0) == null;
  }

  /**
   * Process the words of a procedure division line, maintaining the open scopes.
   *
   * @param depth the depth of the line, or -1 to compute it from the first word
   * @return the depth of the line
   */
  private int procWords(int index, SourceLine line, int from, int depth) {
    List<Word> words = line.getWords();
    for (int i = from; i < words.size(); i++) {
      Word word = words.get(i);
      String upper = word.upper();
      boolean first = depth < 0;
      if (word.isPunctuation()) {
        if (first) {
          depth = sentenceOpen ? anchor + 1 : bodyDepth();
        }
        afterWord(word);
        continue;
      }
      if (isBlockStart(upper)) {
        boolean exec = "EXEC".equals(upper) || "EXECUTE".equals(upper);
        if (exec) {
          popSoft();
        }
        if (first) {
          depth = bodyDepth();
          anchor = exec ? depth : anchor;
        }
        line.setMode(SourceLine.Mode.SHIFT);
        inExec = exec;
        inCopy = !exec;
        pseudoText = false;
        sentenceOpen |= exec;
        int end = scanBlock(words, i + 1);
        if (end < 0) {
          return depth;
        }
        i = end;
        if (exec && words.get(end).endsWithPeriod()) {
          endSentence();
        }
        continue;
      }
      Phrase phrase = phrase(words, i);
      if (phrase != null) {
        Frame owner = phrase.owner;
        while (frames.peek() != owner) {
          frames.pop();
        }
        owner.soft = false;
        if (first) {
          depth = owner.open + 1;
          anchor = depth;
        }
        owner.body = (first ? depth : Math.min(depth, owner.open + 1)) + 1;
        i += phrase.length - 1;
        afterWord(words.get(i));
        continue;
      }
      if ("ELSE".equals(upper)) {
        Frame frame = popUntil(Collections.singleton("IF"));
        if (frame != null) {
          frame.body = frame.open + 1;
        }
        if (first) {
          depth = frame != null ? frame.open : bodyDepth();
          anchor = depth;
        }
        afterWord(word);
        continue;
      }
      Frame selection = "WHEN".equals(upper) ? popUntil(SELECTIONS) : null;
      if (selection != null) {
        selection.body = selection.open + 2;
        if (first) {
          depth = selection.open + 1;
          anchor = depth;
        }
        afterWord(word);
        continue;
      }
      String scope = Keywords.SCOPE_TERMINATORS.get(upper);
      if (scope != null) {
        Frame frame = find(scope);
        if (first) {
          depth = frame != null ? frame.open : bodyDepth();
          anchor = depth;
        }
        if (frame != null) {
          popThrough(frame);
        }
        afterWord(word);
        continue;
      }
      if (Keywords.VERBS.contains(upper) || isNextSentence(words, i)) {
        popSoft();
        int open = bodyDepth();
        if (first) {
          depth = open;
          anchor = depth;
        }
        openScope(upper, open, streamStart.get(index) + i);
        afterWord(word);
        continue;
      }
      if (first) {
        depth = sentenceOpen ? anchor + 1 : bodyDepth();
      }
      afterWord(word);
    }
    return depth < 0 ? bodyDepth() : depth;
  }

  private void openScope(String verb, int open, int position) {
    switch (verb) {
      case "IF":
      case "EVALUATE":
      case "SEARCH":
        frames.push(new Frame(verb, open, open + 1, false));
        break;
      case "PERFORM":
        if (isInlinePerform(position)) {
          frames.push(new Frame(verb, open, open + 1, false));
        }
        break;
      default:
        if (Keywords.PHRASE_VERBS.contains(verb)) {
          frames.push(new Frame(verb, open, open + 1, true));
        }
    }
  }

  private boolean isInlinePerform(int position) {
    Word perform = stream.get(position);
    if (perform.endsWithPeriod() || position + 1 >= stream.size()) {
      return false;
    }
    Word next = stream.get(position + 1);
    String upper = next.upper();
    if (Keywords.INLINE_PERFORM_OPTIONS.contains(upper)
        || Keywords.VERBS.contains(upper)
        || "END-PERFORM".equals(upper)) {
      return true;
    }
    return !next.isPunctuation()
        && !next.endsWithPeriod()
        && position + 2 < stream.size()
        && "TIMES".equals(stream.get(position + 2).upper());
  }

  private static boolean isNextSentence(List<Word> words, int i) {
    return "NEXT".equals(words.get(i).upper())
        && i + 1 < words.size()
        && "SENTENCE".equals(words.get(i + 1).upper());
  }

  private static String upper(List<Word> words, int i) {
    return i < words.size() ? words.get(i).upper() : "";
  }

  /** Recognize a conditional phrase belonging to one of the open statements. */
  private Phrase phrase(List<Word> words, int i) {
    int k = "NOT".equals(upper(words, i)) ? i + 1 : i;
    String a = upper(words, k);
    String b = upper(words, k + 1);
    String c = upper(words, k + 2);
    String kind = null;
    int length = 1;
    if ("AT".equals(a) && "END".equals(b)) {
      kind = Keywords.AT_END;
      length = 2;
    } else if ("AT".equals(a) && ("END-OF-PAGE".equals(b) || "EOP".equals(b))) {
      kind = Keywords.END_OF_PAGE;
      length = 2;
    } else if ("END".equals(a)) {
      kind = Keywords.AT_END;
    } else if ("END-OF-PAGE".equals(a) || "EOP".equals(a)) {
      kind = Keywords.END_OF_PAGE;
    } else if ("INVALID".equals(a)) {
      kind = Keywords.INVALID_KEY;
      length = "KEY".equals(b) ? 2 : 1;
    } else if ("ON".equals(a) && "SIZE".equals(b) && "ERROR".equals(c)) {
      kind = Keywords.SIZE_ERROR;
      length = 3;
    } else if ("SIZE".equals(a) && "ERROR".equals(b)) {
      kind = Keywords.SIZE_ERROR;
      length = 2;
    } else if ("ON".equals(a) && ("EXCEPTION".equals(b) || "OVERFLOW".equals(b))) {
      kind = "EXCEPTION".equals(b) ? Keywords.EXCEPTION : Keywords.OVERFLOW;
      length = 2;
    } else if ("EXCEPTION".equals(a) || "OVERFLOW".equals(a)) {
      kind = "EXCEPTION".equals(a) ? Keywords.EXCEPTION : Keywords.OVERFLOW;
    }
    if (kind == null || k + length > words.size()) {
      return null;
    }
    Set<String> owners = Keywords.PHRASE_OWNERS.get(kind);
    for (Frame frame : frames) {
      if (owners.contains(frame.kind)) {
        return new Phrase(frame, k - i + length);
      }
      if (!Keywords.PHRASE_VERBS.contains(frame.kind)) {
        return null;
      }
    }
    return null;
  }

  private void afterWord(Word word) {
    sentenceOpen = true;
    if (word.endsWithPeriod()) {
      endSentence();
    }
  }

  private void endSentence() {
    frames.clear();
    sentenceOpen = false;
    anchor = 0;
  }

  private void popSoft() {
    while (!frames.isEmpty() && frames.peek().soft) {
      frames.pop();
    }
  }

  private int bodyDepth() {
    for (Frame frame : frames) {
      if (!frame.soft) {
        return frame.body;
      }
    }
    return 0;
  }

  private Frame find(String kind) {
    for (Frame frame : frames) {
      if (frame.kind.equals(kind)) {
        return frame;
      }
    }
    return null;
  }

  /** Close the scope together with the scopes nested into it. */
  private void popThrough(Frame frame) {
    Frame popped;
    do {
      popped = frames.pop();
    } while (popped != frame);
  }

  /** Close the scopes above the innermost scope of the given kinds. */
  private Frame popUntil(Set<String> kinds) {
    for (Frame frame : frames) {
      if (kinds.contains(frame.kind)) {
        while (frames.peek() != frame) {
          frames.pop();
        }
        return frame;
      }
    }
    return null;
  }

  // ------------------------------------------------- COPY, REPLACE and EXEC

  private static boolean isBlockStart(String upper) {
    return "COPY".equals(upper)
        || "REPLACE".equals(upper)
        || "EXEC".equals(upper)
        || "EXECUTE".equals(upper);
  }

  /** Start a COPY, REPLACE or EXEC block on a line outside the procedure division. */
  private void startBlock(SourceLine line, int column) {
    String first = line.upper(0);
    inExec = "EXEC".equals(first) || "EXECUTE".equals(first);
    inCopy = !inExec;
    pseudoText = false;
    shift(line, column - line.originalIndent());
    if (scanBlock(line.getWords(), 1) < 0) {
      blockShift = line.getShift();
    }
  }

  private void continueBlock(int index, SourceLine line) {
    boolean exec = inExec;
    shift(line, blockShift);
    int end = scanBlock(line.getWords(), 0);
    if (end >= 0 && division == Division.PROCEDURE) {
      if (exec && line.getWords().get(end).endsWithPeriod()) {
        endSentence();
      }
      procWords(index, line, end + 1, bodyDepth());
    }
  }

  /**
   * Find the end of the current block.
   *
   * @return index of the last word of the block or -1 if the block continues on the next line
   */
  private int scanBlock(List<Word> words, int from) {
    for (int i = from; i < words.size(); i++) {
      Word word = words.get(i);
      if (inExec) {
        if ("END-EXEC".equals(word.upper())) {
          inExec = false;
          return i;
        }
        continue;
      }
      if (!word.isLiteral() && StringUtils.countMatches(word.getText(), "==") % 2 == 1) {
        pseudoText = !pseudoText;
      }
      if (!pseudoText && word.endsWithPeriod()) {
        inCopy = false;
        return i;
      }
    }
    return -1;
  }

  // ------------------------------------------------------ rendering choices

  private void header(SourceLine line) {
    line.setHeader(true);
    tokens(line, 0, headerContinuation);
  }

  private void tokens(SourceLine line, int column, int wrapColumn) {
    line.setMode(line.isFrozen() ? SourceLine.Mode.VERBATIM : SourceLine.Mode.TOKENS);
    line.setColumn(column);
    line.setWrapColumn(wrapColumn);
  }

  private void shift(SourceLine line, int shift) {
    line.setMode(line.isFrozen() ? SourceLine.Mode.VERBATIM : SourceLine.Mode.SHIFT);
    line.setShift(shift);
  }

  private void finish(SourceLine line, int column, int wrapColumn) {
    if (line.getMode() != SourceLine.Mode.SHIFT) {
      tokens(line, column, wrapColumn);
      return;
    }
    shift(line, column - line.originalIndent());
    if (inExec || inCopy) {
      blockShift = line.getShift();
    }
  }

  /** Apply the standalone periods option to the procedure division statements. */
  private void periods() {
    for (int i = 0; i < lines.size(); i++) {
      SourceLine line = lines.get(i);
      if (!line.isProcedure() || line.getMode() != SourceLine.Mode.TOKENS || line.isHeader()) {
        continue;
      }
      if (settings.isStandalonePeriods()) {
        line.setSplitPeriod(!line.isPeriodOnly() && line.endsWithPeriod());
      } else if (line.isPeriodOnly() && line.getComment() == null && i > 0) {
        SourceLine previous = lines.get(i - 1);
        List<Word> words = previous.getWords();
        if (previous.isProcedure()
            && previous.getMode() == SourceLine.Mode.TOKENS
            && !previous.isHeader()
            && !previous.isPeriodOnly()
            && previous.getComment() == null
            && words.get(words.size() - 1).getSeparator().isEmpty()) {
          previous.setAppendPeriod(true);
          line.setMode(SourceLine.Mode.REMOVED);
        }
      }
    }
  }

  // -------------------------------------------------------------- rendering

  private void render(SourceLine line, List<String> result) {
    if (line.getKind() != SourceLine.Kind.CODE) {
      result.add(verbatim(line));
      return;
    }
    List<String> contents;
    switch (line.getMode()) {
      case REMOVED:
        return;
      case TOKENS:
        contents = tokenContents(line);
        break;
      case ENTRY:
        contents = line.getRendered();
        break;
      case SHIFT:
        contents = Collections.singletonList(shifted(line));
        break;
      default:
        contents = null;
    }
    if (contents == null || contents.isEmpty()) {
      result.add(verbatim(line));
      return;
    }
    for (int i = 0; i < contents.size(); i++) {
      result.add(compose(line, contents.get(i), i == 0));
    }
  }

  private List<String> tokenContents(SourceLine line) {
    List<Word> words = new ArrayList<>(line.getWords());
    int last = words.size() - 1;
    if (line.isAppendPeriod()) {
      words.set(last, words.get(last).withPeriod());
    }
    if (line.isSplitPeriod()) {
      if (words.get(last).isPunctuation()) {
        words.remove(last);
      } else {
        words.set(last, words.get(last).withoutSeparator());
      }
    }
    boolean spaced = settings.isSpaceBeforePunctuation() && !line.isHeader();
    List<String> contents =
        renderer.layout(
            line.getColumn(),
            line.getWrapColumn(),
            words,
            line.getComment(),
            spaced,
            word -> !line.isProcedure() || isContinuationWord(word));
    if (contents == null) {
      // keep the original content, and the period of the removed line if it was merged here
      contents = new ArrayList<>(Collections.singletonList(line.getContent()));
      if (line.isAppendPeriod()) {
        contents.add(renderer.indent(areaB) + ".");
      }
    } else if (line.isSplitPeriod()) {
      contents.add(renderer.indent(areaB) + ".");
    }
    return contents;
  }

  /**
   * Check whether a word can start a wrapped line of a statement, so that formatting the result
   * again treats the line as a continuation too.
   */
  private static boolean isContinuationWord(Word word) {
    String upper = word.upper();
    return !word.isPunctuation()
        && !Keywords.VERBS.contains(upper)
        && !Keywords.SCOPE_TERMINATORS.containsKey(upper)
        && !Keywords.PHRASE_STARTS.contains(upper)
        && !"ELSE".equals(upper)
        && !"WHEN".equals(upper)
        && !"NEXT".equals(upper)
        && !isBlockStart(upper);
  }

  private String shifted(SourceLine line) {
    String content = StringUtils.stripEnd(line.getContent(), null);
    int current = line.originalIndent();
    int target = Math.max(0, current + line.getShift());
    if (fixed && !line.isHeader() && current >= FIXED_AREA_B) {
      target = Math.max(target, FIXED_AREA_B);
    }
    String text = content.substring(Math.min(current, content.length()));
    if (target + text.length() > renderer.width()) {
      return content;
    }
    return renderer.indent(target) + text;
  }

  private String compose(SourceLine line, String content, boolean first) {
    String body = StringUtils.stripEnd(content, null);
    if (!fixed) {
      return body;
    }
    boolean preserve = settings.isPreserveSequenceNumbers();
    String sequence =
        preserve && first
            ? StringUtils.rightPad(line.getSequence(), SEQUENCE_LENGTH)
            : BLANK_SEQUENCE;
    String identification = preserve && first ? line.getIdentification() : "";
    if (!identification.isEmpty()) {
      return sequence
          + line.getIndicator()
          + StringUtils.rightPad(body, CONTENT_END - CONTENT_START)
          + identification;
    }
    return StringUtils.stripEnd(sequence + line.getIndicator() + body, null);
  }

  /** The original line, without sequence numbers when they are not preserved. */
  private String verbatim(SourceLine line) {
    if (!fixed
        || settings.isPreserveSequenceNumbers()
        || (line.getKind() == SourceLine.Kind.DIRECTIVE && isProcessStatement(line))) {
      return line.getOriginal();
    }
    if (line.getOriginal().length() <= SEQUENCE_LENGTH) {
      return "";
    }
    String result = BLANK_SEQUENCE + line.getIndicator() + line.getContent();
    // trailing spaces of a continued literal are significant
    return line.isFrozen() ? result : StringUtils.stripEnd(result, null);
  }
}
