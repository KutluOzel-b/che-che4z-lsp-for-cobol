       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBACT01C.
       PROCEDURE DIVISION.
           IF A > B THEN
             EXEC CICS HANDLE ABEND LABEL(HANDLE-ABEND)
             END-EXEC
             GO TO CALCULATION
           END-IF.

       HANDLE-ABEND.