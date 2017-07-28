/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package flavt.slg.lib.communication;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import javax.swing.Timer;
import org.apache.log4j.Logger;

/**
 * Thread-class for COM-port listener
 */
public class SocketReader implements Runnable 
{
    static Logger logger = Logger.getLogger(SocketReader.class);
    
    private final InputStream m_is;
    private final ObjectInputStream m_ois;
    
    TwoWaySocket pTwoWaySocket;
    
    private boolean m_bContinue;

    public void StopThread() { m_bContinue = false;}
    
    public SocketReader( InputStream is, ObjectInputStream ois, TwoWaySocket pParent)
    {
        m_is = is;
        m_ois = ois;
        pTwoWaySocket = pParent;
    }
        
    public void run ()
    {
        m_bContinue = true;
        
        logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "In");
        
        try {
            
            logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "before while");
            
            while( m_bContinue) {
                
                /**TODO
                 * QUIT processing
                 */
                /*
                if( pParent != null &&
                                pParent.GetCmdInAction() != null &&
                                pParent.GetCmdInAction().equals( "QUIT")) {
                        
                        m_bContinue = false;
                        break;
                    }
                */
                
                if( pTwoWaySocket.GetCmdInAction() != null) {
                    if( hvv_timeouts.HVV_TimeoutsManager.getInstance().CheckTimeout(pTwoWaySocket.m_lTimeOutId) == true) {
                        logger.info( pTwoWaySocket.m_pHvvComm.m_strMarker + "TimeOut happens for id=" + pTwoWaySocket.m_lTimeOutId + "  'REQ." + pTwoWaySocket.GetCmdInAction() + "' command!");
                        hvv_timeouts.HVV_TimeoutsManager.getInstance().RemoveId(pTwoWaySocket.m_lTimeOutId);
                        pTwoWaySocket.m_lTimeOutId = 0;
                        
                        //PROCESS TimeOut
                        pTwoWaySocket.GetCmdInAction().GetProcessor().processTimeOut();
                        
                        //Increase and check timeouts counter
                        pTwoWaySocket.m_nTimeoutCounter++;
                        if( pTwoWaySocket.m_nTimeoutCounter >= 10) {
                            logger.warn( pTwoWaySocket.m_pHvvComm.m_strMarker + "10 consequitive timeouts! Pending disconnection...!");
                            
                            new Thread( new Runnable() {

                                @Override
                                public void run() {
                                    logger.info( pTwoWaySocket.m_pHvvComm.m_strMarker + "..PENDED RxRx disconnection...");
                                    try {
                                        pTwoWaySocket.disconnect();
                                    } catch( Exception ex) {
                                        logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "Exception caught on disconnecting after 10 consequitive timeouts!", ex);
                                    }
                                    logger.info( pTwoWaySocket.m_pHvvComm.m_strMarker + "...OVER");
                                }
                            }).start();
                            
                            //set state
                            pTwoWaySocket.m_pHvvComm.SetState( SLG_Comm_client.STATE_DISCONNECTED);
                            
                            //self stop
                            m_bContinue = false;
                        }
                        else if( pTwoWaySocket.m_nTimeoutCounter > 3) {
                            logger.warn( pTwoWaySocket.m_pHvvComm.m_strMarker + pTwoWaySocket.m_nTimeoutCounter + " consequitive timeouts! Idle!");
                            pTwoWaySocket.m_pHvvComm.SetState( SLG_Comm_client.STATE_CONNECTED_IDLE);
                        }
                        
                        //drop current action-command
                        pTwoWaySocket.SetCmdInAction( null);
                        
                    }
                    else {
                        if( m_is.available() > 0) {
                
                            try {
                                
                                //RESPONSE ID
                                String strId = ( String) this.m_ois.readObject();
                                String strLog = "<< [" + strId + ";";
                                
                                pTwoWaySocket.m_nTimeoutCounter = 0;
                                pTwoWaySocket.m_pHvvComm.SetState( SLG_Comm_client.STATE_CONNECTED_OK);

                                //RESPONSE PARCEL LENGTH
                                int nParcelLength = this.m_ois.readInt();
                                strLog += nParcelLength + ";";
                                
                                LinkedList lstResponseParcel = new LinkedList();
                                for( int i = 0; i < nParcelLength; i++) {
                                    Object obj = this.m_ois.readObject();
                                    lstResponseParcel.addLast( obj);
                                    strLog += obj + ";";
                                }
                                strLog += "]";
                                logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + strLog);
                                
                                if( pTwoWaySocket.GetCmdInAction().GetCommandId().equals( strId) ) {
                                    logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "Got an answer for correct request id!");
                                    
                                    //Drop timeout
                                    hvv_timeouts.HVV_TimeoutsManager.getInstance().RemoveId( pTwoWaySocket.m_lTimeOutId);
                                    pTwoWaySocket.m_lTimeOutId = 0;

                                    //обработаем полученный ответ
                                    if( pTwoWaySocket.GetCmdInAction().GetProcessor() != null)
                                        pTwoWaySocket.GetCmdInAction().GetProcessor().processResponse( lstResponseParcel);
                                    
                                    //сбросим текущую транзакцию обмена командами
                                    pTwoWaySocket.SetCmdInAction( null);
                                }
                                else {
                                    logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "ID текущей активной команды=" + pTwoWaySocket.GetCmdInAction().GetCommandId());
                                    logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "ID команды, на которую получен ответ=" + strId);
                                    logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "Тем не менее, продолжаем ждать ответ на текущую активную команду (или таймаут)!");
                                }
                            }
                            catch( ClassNotFoundException ex) {
                                logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "ClassNotFoundException caught!", ex);
                                m_bContinue = false;
                            }
                            catch( EOFException ex) {
                                logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "EOFException caught!", ex);
                                m_bContinue = false;
                            }
                        }
                        else {
                            logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "Команда отправлена. Ждём ответ. Available bytes = 0");
                            Thread.sleep( 100);
                        }
                    }
                }
                else {
                    logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "Нет отправленной команды");
                    Thread.sleep( 100);
                }
                
                
            }
            
            //something new
            //something new2
            logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "after while");
            
        }
        catch ( IOException ex) {
            logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "IOException caught!", ex);
        }
        catch ( InterruptedException ex) {
            logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "InterruptedException caught!", ex);
        }
        
        logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "Out");
    }
}