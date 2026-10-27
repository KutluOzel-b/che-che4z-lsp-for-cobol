       IDENTIFICATION DIVISION.
       PROGRAM-ID. CKRM614.
       AUTHOR. O'BRIEN  AND  CO.                                        
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO "INFILE"
              ORGANIZATION IS SEQUENTIAL .
       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE
           RECORDING MODE IS F .
       01  IN-REC PIC X(80) .
       WORKING-STORAGE SECTION.
      *    COMMENT LINE STAYS AS IS
       01  WS-FLAGS .
          05 WS-EOF                    PIC X               VALUE "N" .
             88 END-OF-FILE                                VALUE "Y" .
          05 WS-COUNTER                PIC S9(4)    COMP   VALUE ZERO .
          05 WS-TOTAL-AMOUNT-LONG-NAME PIC S9(7)V99 COMP-3 VALUE 0 .
          05 WS-TABLE OCCURS 10 TIMES
             INDEXED BY WS-IDX .
             10 WS-ITEM                PIC X(5) .
          05 WS-CODE                   PIC X .
             88 VALID-CODE VALUES "A" "B" "C"
                                  "D" "E" .
       77 WS-MSG PIC X(60) VALUE 'THIS IS A VERY LONG LITERAL THAT IS CO
      -    'NTINUED ON THE NEXT LINE'.                                  
       77  WS-QUOTE PIC X(10) VALUE "IT'S OK" .
           EXEC SQL INCLUDE SQLCA END-EXEC.
           COPY CKRMCPY.
       PROCEDURE DIVISION.
       MAIN-PARA.
           OPEN INPUT IN-FILE
           PERFORM UNTIL END-OF-FILE
              READ IN-FILE
                 AT END
                    SET END-OF-FILE TO TRUE
                 NOT AT END
                    ADD 1 TO WS-COUNTER
              END-READ
              IF WS-CODE = "A" AND
                 WS-COUNTER > 5
                 MOVE WS-CODE TO WS-ITEM (1) , WS-ITEM (2)
              ELSE
                 EVALUATE TRUE
                    WHEN VALID-CODE
                       DISPLAY "VALID"
                    WHEN OTHER
                       DISPLAY "INVALID" WS-CODE
                 END-EVALUATE
              END-IF
           END-PERFORM
           EXEC SQL
             SELECT COL1 INTO :WS-ITEM
               FROM TAB1 WHERE COL2 = 'X'
           END-EXEC
           PERFORM SUB-PARA THRU SUB-EXIT
           CLOSE IN-FILE
           GOBACK
           .
       SUB-PARA.
           COMPUTE WS-TOTAL-AMOUNT-LONG-NAME = WS-COUNTER * 2
              ON SIZE ERROR DISPLAY "OVERFLOW" END-COMPUTE
           .
       SUB-EXIT.
           EXIT
           .
