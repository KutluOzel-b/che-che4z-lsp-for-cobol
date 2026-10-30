       IDENTIFICATION DIVISION.                                                 
       PROGRAM-ID. SLICKB0.                                                     
       AUTHOR.     Ira M. Slick.                                                
                                                                                
      ******************************************************************        
      *DESCRIPTION: Batch Utility Main Driver                          *        
      ******************************************************************        
                                                                                
       ENVIRONMENT DIVISION.                                                    
       CONFIGURATION SECTION.                                                   
       SOURCE-COMPUTER.    IBM-PC.                                              
       OBJECT-COMPUTER.    IBM-PC.                                              
       INPUT-OUTPUT SECTION.                                                    
       FILE-CONTROL.                                                            
           SELECT ACCTFILE                                                      
               ASSIGN        TO UT-ACCTFILE                                     
               ORGANIZATION  IS INDEXED                                         
               ACCESS MODE   IS DYNAMIC                                         
               RECORD KEY    IS ACCOUNT-KEY                                     
               FILE STATUS   IS FS-ACCTFILE.                                    
           SELECT CTLFILE                                                       
               ASSIGN        TO UT-CTLFILE                                      
               ORGANIZATION  IS SEQUENTIAL                                      
               ACCESS MODE   IS SEQUENTIAL                                      
               FILE STATUS   IS FS-CTLFILE.                                     
           SELECT RPTFILE                                                       
               ASSIGN        TO UT-RPTFILE                                      
               ORGANIZATION  IS SEQUENTIAL                                      
               ACCESS MODE   IS SEQUENTIAL                                      
               FILE STATUS   IS FS-RPTFILE.                                     
                                                                                
       DATA DIVISION.                                                           
       FILE SECTION.                                                            
       FD  ACCTFILE                          IS EXTERNAL                        
           DATA RECORD IS ACCOUNT-RECORD.                                       
           COPY ACCTFILE.                                                       
       FD  CTLFILE                                                              
           DATA RECORD IS CONTROL-RECORD                                        
           RECORD CONTAINS 0 CHARACTERS                                         
           BLOCK CONTAINS 0 RECORDS.                                            
           COPY CTLFILE.                                                        
       FD  RPTFILE                           IS EXTERNAL                        
           DATA RECORD IS REPORT-RECORD                                         
           BLOCK CONTAINS 0 RECORDS.                                            
           COPY RPTFILE.                                                        
                                                                                
       WORKING-STORAGE SECTION.                                                 
                                                                                
           COPY SLICKCOM.                                                       
                                                                                
       77  FS-ACCTFILE                    PIC 9(02) VALUE ZERO.                 
       77  FS-CTLFILE                     PIC 9(02) VALUE ZERO.                 
       77  FS-RPTFILE                     PIC 9(02) VALUE ZERO.                 
       77  WS-RETURN-CODE                 PIC 9(04) VALUE ZERO.                 
       77  WS-ERROR-COUNT                 PIC 9(04) VALUE ZERO.                 
       77  WS-WARNING-COUNT               PIC 9(04) VALUE ZERO.                 
       77  WS-ACCOUNT-ID                  PIC 9(04) VALUE ZERO.                 
       77  WS-TELEPHONE-1                 PIC 9(10) VALUE ZERO.                 
                                                                                
       LINKAGE SECTION.                                                         
                                                                                
       PROCEDURE DIVISION.                                                      
                                                                                
       000-START-PROCESSING.                                                    
           ACCEPT COMM-TRAN-DATE FROM DATE.                                     
           PERFORM 000-OPEN-ACCTFILE.                                           
           PERFORM 000-OPEN-CTLFILE.                                            
           PERFORM 000-OPEN-RPTFILE.                                            
           PERFORM 000-PROCESS-REQUESTS.                                        
           PERFORM 000-TERMINATION.                                             
           MOVE WS-RETURN-CODE TO RETURN-CODE.                                  
           GOBACK.                                                              
                                                                                
       000-OPEN-CTLFILE.                                                        
           OPEN INPUT CTLFILE.                                                  
           IF FS-CTLFILE NOT = ZERO                                             
               DISPLAY '*Error* ' FS-CTLFILE  ' Opening CTLFILE'                
               ADD 1 TO WS-ERROR-COUNT.                                         
                                                                                
       000-OPEN-RPTFILE.                                                        
           OPEN OUTPUT RPTFILE.                                                 
           IF FS-RPTFILE NOT = ZERO                                             
               DISPLAY '*Error* ' FS-RPTFILE  ' Opening RPTFILE'                
               ADD 1 TO WS-ERROR-COUNT.                                         
                                                                                
       000-OPEN-ACCTFILE.                                                       
           OPEN I-O ACCTFILE.                                                   
           IF FS-ACCTFILE NOT = ZERO                                            
               DISPLAY '*Error* ' FS-ACCTFILE ' Opening ACCTFILE'               
               ADD 1 TO WS-ERROR-COUNT                                          
           ELSE                                                                 
               PERFORM 000-VERIFY-ACCTFILE.                                     
                                                                                
       000-VERIFY-ACCTFILE.                                                     
           INITIALIZE ACCOUNT-KEY.                                              
           READ ACCTFILE.                                                       
           IF FS-ACCTFILE NOT = ZERO                                            
               PERFORM 000-INITIALIZE-ACCTFILE                                  
           ELSE                                                                 
               MOVE A-C-MIN-ACCOUNT-ID TO COMM-MIN-ACCOUNT-ID                   
               MOVE A-C-MAX-ACCOUNT-ID TO COMM-MAX-ACCOUNT-ID.                  
                                                                                
       000-INITIALIZE-ACCTFILE.                                                 
           MOVE 1001                   TO A-C-MIN-ACCOUNT-ID.                   
           MOVE 1000                   TO A-C-MAX-ACCOUNT-ID.                   
           WRITE ACCOUNT-CONTROL-RECORD.                                        
           IF FS-ACCTFILE NOT = ZERO                                            
               DISPLAY '*Error* ' FS-ACCTFILE ' Initializing ACCTFILE'          
               ADD 1 TO WS-ERROR-COUNT.                                         
                                                                                
       000-PROCESS-REQUESTS.                                                    
           IF WS-ERROR-COUNT = ZERO                                             
               PERFORM 000-PROCESS-CONTROL-RECORD                               
                   UNTIL FS-CTLFILE NOT = ZERO.                                 
                                                                                
       000-PROCESS-CONTROL-RECORD.                                              
           READ CTLFILE.                                                        
           IF FS-CTLFILE = ZERO                                                 
               DISPLAY CONTROL-RECORD (2:120)                                   
               IF CONTROL-COMMENT NOT = '*'                                     
                   EVALUATE CONTROL-RECORD-TYPE                                 
                   WHEN 'add'                                                   
                       MOVE ZERO                  TO WS-ACCOUNT-ID              
                       MOVE C-A-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE = ZERO                                    
                           DISPLAY '*Warning* Account already exists.'          
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           PERFORM 000-ADD                                      
                       END-IF                                                   
                   WHEN 'update'                                                
                       MOVE C-U-ID                TO WS-ACCOUNT-ID              
                       MOVE C-U-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 900-FUTURE-REQUEST                           
                       END-IF                                                   
                   WHEN 'delivery'                                              
                       MOVE C-D-ID                TO WS-ACCOUNT-ID              
                       MOVE C-D-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 000-DELIVERY                                 
                       END-IF                                                   
                   WHEN 'payment'                                               
                       MOVE C-P-ID                TO WS-ACCOUNT-ID              
                       MOVE C-P-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 000-PAYMENT                                  
                       END-IF                                                   
                   WHEN 'service'                                               
                       MOVE C-M-ID                TO WS-ACCOUNT-ID              
                       MOVE C-M-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 000-SERVICE                                  
                       END-IF                                                   
                   WHEN 'history'                                               
                       MOVE C-H-ID                TO WS-ACCOUNT-ID              
                       MOVE C-H-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 900-FUTURE-REQUEST                           
                       END-IF                                                   
                   WHEN 'status'                                                
                       MOVE C-S-ID                TO WS-ACCOUNT-ID              
                       MOVE C-S-HOME-TELEPHONE    TO WS-TELEPHONE-1             
                       PERFORM 000-GET-ACCOUNT-ID                               
                       IF RETURN-CODE > ZERO                                    
                           DISPLAY '*Warning* Account not found.'               
                           ADD 1 TO WS-WARNING-COUNT                            
                       ELSE                                                     
                           MOVE A-S-ACCOUNT-ID    TO WS-ACCOUNT-ID              
                           MOVE A-S-TELEPHONE-1   TO WS-TELEPHONE-1             
                           PERFORM 900-FUTURE-REQUEST                           
                       END-IF                                                   
                   WHEN 'list'                                                  
                       PERFORM 900-FUTURE-REQUEST                               
                   WHEN OTHER                                                   
                       PERFORM 900-INVALID-REQUEST                              
                   END-EVALUATE.                                                
                                                                                
       000-GET-ACCOUNT-ID.                                                      
           CALL 'SLICKBA' USING         SLICK-COMM                              
                                        WS-ACCOUNT-ID                           
                                        WS-TELEPHONE-1.                         
                                                                                
       000-ADD.                                                                 
               CALL 'SLICKB1' USING     SLICK-COMM                              
                                        C-A-LAST-NAME                           
                                        C-A-FIRST-NAME                          
                                        C-A-MIDDLE-INITIAL                      
                                        C-A-STREET-ADDRESS                      
                                        C-A-CITY                                
                                        C-A-STATE                               
                                        C-A-ZIP-CODE                            
                                        C-A-ZIP-PLUS-4                          
                                        C-A-HOME-TELEPHONE                      
                                        C-A-WORK-TELEPHONE                      
                                        C-A-HOME-SIZE                           
                                        C-A-TANK-SIZE                           
                                        C-A-RATE                                
               EVALUATE                                                         
                    RETURN-CODE                                                 
               WHEN 8                                                           
                   ADD 1 TO WS-ERROR-COUNT                                      
               WHEN 4                                                           
                   ADD 1 TO WS-WARNING-COUNT                                    
               WHEN 0                                                           
                   MOVE A-C-MIN-ACCOUNT-ID  TO COMM-MIN-ACCOUNT-ID              
                   MOVE A-C-MAX-ACCOUNT-ID  TO COMM-MAX-ACCOUNT-ID              
               END-EVALUATE.                                                    
                                                                                
       000-DELIVERY.                                                            
               CALL 'SLICKBD' USING     SLICK-COMM                              
                                        WS-ACCOUNT-ID                           
                                        WS-TELEPHONE-1                          
                                        C-D-DATE                                
                                        C-D-EMPLOYEE-ID                         
                                        C-D-GALLONS                             
                                        C-D-RATE                                
               EVALUATE                                                         
                    RETURN-CODE                                                 
               WHEN 8                                                           
                   ADD 1 TO WS-ERROR-COUNT                                      
               WHEN 4                                                           
                   ADD 1 TO WS-WARNING-COUNT                                    
               END-EVALUATE.                                                    
                                                                                
       000-PAYMENT.                                                             
               CALL 'SLICKBP' USING     SLICK-COMM                              
                                        WS-ACCOUNT-ID                           
                                        WS-TELEPHONE-1                          
                                        C-P-DATE                                
                                        C-P-AMOUNT                              
               EVALUATE                                                         
                    RETURN-CODE                                                 
               WHEN 8                                                           
                   ADD 1 TO WS-ERROR-COUNT                                      
               WHEN 4                                                           
                   ADD 1 TO WS-WARNING-COUNT                                    
               END-EVALUATE.                                                    
                                                                                
       000-SERVICE.                                                             
               CALL 'SLICKBS' USING     SLICK-COMM                              
                                        WS-ACCOUNT-ID                           
                                        WS-TELEPHONE-1                          
                                        C-M-DATE                                
                                        C-M-EMPLOYEE-ID                         
                                        C-M-SERVICE-CODE                        
                                        C-M-REPAIR-CODE                         
                                        C-M-PART-CODE-1                         
                                        C-M-PART-CODE-2                         
                                        C-M-PART-CODE-3                         
                                        C-M-PART-CODE-4                         
                                        C-M-PART-CODE-5                         
                                        C-M-CHARGE                              
               EVALUATE                                                         
                    RETURN-CODE                                                 
               WHEN 8                                                           
                   ADD 1 TO WS-ERROR-COUNT                                      
               WHEN 4                                                           
                   ADD 1 TO WS-WARNING-COUNT                                    
               END-EVALUATE.                                                    
                                                                                
       000-TERMINATION.                                                         
           IF WS-ERROR-COUNT > 0                                                
               MOVE 8                   TO WS-RETURN-CODE                       
           ELSE                                                                 
               IF WS-WARNING-COUNT > 0                                          
                   MOVE 4               TO WS-RETURN-CODE                       
           ELSE                                                                 
                   MOVE 0               TO WS-RETURN-CODE.                      
           DISPLAY                                                              
               'Processing complete.'                                           
               SPACE                                                            
               'Warnings:'                                                      
               SPACE                                                            
               WS-WARNING-COUNT                                                 
               SPACE                                                            
               'Errors:'                                                        
               SPACE                                                            
               WS-ERROR-COUNT                                                   
               SPACE                                                            
               'Return Code:'                                                   
               SPACE                                                            
               WS-RETURN-CODE.                                                  
           CLOSE ACCTFILE.                                                      
           CLOSE CTLFILE.                                                       
           CLOSE RPTFILE.                                                       
                                                                                
       900-INVALID-REQUEST.                                                     
           DISPLAY '*Error* Invalid request type: '                             
               CONTROL-RECORD-TYPE.                                             
           ADD 1 TO WS-ERROR-COUNT.                                             
                                                                                
       900-FUTURE-REQUEST.                                                      
           DISPLAY '*Warning* Request type not yet supported: '                 
               CONTROL-RECORD-TYPE.                                             
           ADD 1 TO WS-WARNING-COUNT.                                           
                                                                                
