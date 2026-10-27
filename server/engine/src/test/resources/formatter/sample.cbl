000100 IDENTIFICATION DIVISION.                                         CKRM614
000200 PROGRAM-ID. CKRM614.                                             CKRM614
000300 AUTHOR. O'BRIEN  AND  CO.                                        CKRM614
000400 ENVIRONMENT DIVISION.                                            CKRM614
000500 INPUT-OUTPUT SECTION.                                            CKRM614
000600 FILE-CONTROL.                                                    CKRM614
000700     SELECT IN-FILE ASSIGN TO "INFILE"                            CKRM614
000800       ORGANIZATION IS SEQUENTIAL.                                CKRM614
000900 DATA DIVISION.                                                   CKRM614
001000 FILE SECTION.                                                    CKRM614
001100 FD IN-FILE                                                       CKRM614
001200     RECORDING MODE IS F.                                         CKRM614
001300 01 IN-REC PIC X(80).                                             CKRM614
001400 WORKING-STORAGE SECTION.                                         CKRM614
001500*    COMMENT LINE STAYS AS IS                                     CKRM614
001600 01  WS-FLAGS.                                                    CKRM614
001700   05 WS-EOF PIC X VALUE "N".                                     CKRM614
001800      88 END-OF-FILE VALUE 'Y'.                                   CKRM614
001900   05 WS-COUNTER PIC S9(4) COMP VALUE ZERO.                       CKRM614
002000   05 WS-TOTAL-AMOUNT-LONG-NAME PIC S9(7)V99 COMP-3 VALUE 0.      CKRM614
002100   05 WS-TABLE OCCURS 10 TIMES                                    CKRM614
002200          INDEXED BY WS-IDX.                                      CKRM614
002300      10 WS-ITEM PIC X(5).                                        CKRM614
002400   05 WS-CODE PIC X.                                              CKRM614
002500      88 VALID-CODE VALUES 'A' 'B' 'C'                            CKRM614
002600                  'D' 'E'.                                        CKRM614
002700 77 WS-MSG PIC X(60) VALUE 'THIS IS A VERY LONG LITERAL THAT IS COCKRM614
002800-    'NTINUED ON THE NEXT LINE'.                                  CKRM614
002900 77 WS-QUOTE PIC X(10) VALUE "IT'S OK".                           CKRM614
003000     EXEC SQL INCLUDE SQLCA END-EXEC.                             CKRM614
003100     COPY CKRMCPY.                                                CKRM614
003200 PROCEDURE DIVISION.                                              CKRM614
003300 MAIN-PARA.                                                       CKRM614
003400     OPEN INPUT IN-FILE                                           CKRM614
003500     PERFORM UNTIL END-OF-FILE                                    CKRM614
003600     READ IN-FILE                                                 CKRM614
003700     AT END                                                       CKRM614
003800     SET END-OF-FILE TO TRUE                                      CKRM614
003900     NOT AT END                                                   CKRM614
004000     ADD 1 TO WS-COUNTER                                          CKRM614
004100     END-READ                                                     CKRM614
004200     IF WS-CODE = "A" AND                                         CKRM614
004300     WS-COUNTER > 5                                               CKRM614
004400     MOVE WS-CODE TO WS-ITEM (1) , WS-ITEM (2)                    CKRM614
004500     ELSE                                                         CKRM614
004600     EVALUATE TRUE                                                CKRM614
004700     WHEN VALID-CODE                                              CKRM614
004800     DISPLAY "VALID"                                              CKRM614
004900     WHEN OTHER                                                   CKRM614
005000     DISPLAY 'INVALID' WS-CODE                                    CKRM614
005100     END-EVALUATE                                                 CKRM614
005200     END-IF                                                       CKRM614
005300     END-PERFORM                                                  CKRM614
005400     EXEC SQL                                                     CKRM614
005500       SELECT COL1 INTO :WS-ITEM                                  CKRM614
005600         FROM TAB1 WHERE COL2 = 'X'                               CKRM614
005700     END-EXEC                                                     CKRM614
005800     PERFORM SUB-PARA THRU SUB-EXIT                               CKRM614
005900     CLOSE IN-FILE                                                CKRM614
006000     GOBACK                                                       CKRM614
006100     .                                                            CKRM614
006200 SUB-PARA.                                                        CKRM614
006300     COMPUTE WS-TOTAL-AMOUNT-LONG-NAME = WS-COUNTER * 2           CKRM614
006400     ON SIZE ERROR DISPLAY "OVERFLOW" END-COMPUTE.                CKRM614
006500 SUB-EXIT.                                                        CKRM614
006600     EXIT.                                                        CKRM614
