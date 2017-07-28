/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package flavt.slg.lib.communication;

import HVV_Communication.CommandItem;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import org.apache.log4j.Logger;

/**
 *
 * @author yaroslav
 */
public class SocketWriter implements Runnable 
{
    static Logger logger = Logger.getLogger(SocketWriter.class);
    ObjectOutputStream out;
        
    private boolean m_bContinue;
    public void StopThread() {
        m_bContinue = false;
    }
    
    TwoWaySocket pTwoWaySocket;
    
    
    public SocketWriter ( ObjectOutputStream out, TwoWaySocket parent)
    {
        this.out = out;
        this.pTwoWaySocket = parent;
    }
        
    public void run ()
    {
        m_bContinue = true;
        
        CommandItem pItem;
        try {
            
            logger.info( pTwoWaySocket.m_pHvvComm.m_strMarker + "before while");
            
            while( m_bContinue) {

                if( pTwoWaySocket.GetCmdInAction() == null) {                 //если мы в этот момент ничего не обрабатываем
                    
                    if( pTwoWaySocket.GetQueue().isEmpty() == false) {            //в очереди команд на обработку что-то есть
                            
                        pItem = ( CommandItem) pTwoWaySocket.GetQueue().poll();
                        if( pItem != null) {
                            
                            logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "Item from queue: '" + pItem + "'!");
                            logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "Queue length: " + pTwoWaySocket.GetQueue().size());
                    
                            //SENDING PARCEL ID
                            this.out.writeObject( pItem.GetCommandId());
                        
                            String strLog = ">> [" + pItem.GetCommandId() + ";";
                            
                            LinkedList lst = pItem.GetParcelOut();
                            int nParcelLength = lst.size();
                            
                            //SENDING PARCEL LENGTH
                            this.out.writeInt( nParcelLength);
                            strLog += "" + nParcelLength +  ";";

                            String strCmd = null;
                            
                            //SENDING PARCEL
                            Iterator it = lst.iterator();
                            while( it.hasNext()) {
                                Object obj = it.next();
                                
                                if( strCmd == null && obj.getClass() == String.class) {
                                    strCmd = ( String) obj;
                                    //logger.debug( "strCmd =" + strCmd);
                                }
                                
                                this.out.writeObject( obj);
                                strLog += "" + obj + ";";
                            }
                    
                            strLog += "]";
                            logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + strLog);
                            
                            if( "QUIT".equals( strCmd)) {
                                logger.debug( "Processing 'QUIT'!");
                                break;
                            }
                            
                            //START TIMEOUT INSTANCE
                            pTwoWaySocket.m_lTimeOutId = hvv_timeouts.HVV_TimeoutsManager.getInstance().StartTimeout( 1000);
                            logger.debug( pTwoWaySocket.m_pHvvComm.m_strMarker + "Started timeout. ID=" + pTwoWaySocket.m_lTimeOutId);
                            
                            pTwoWaySocket.SetCmdInAction( pItem);
                        }
                        else {
                            logger.error( pTwoWaySocket.m_pHvvComm.m_strMarker + "3. Item from queue is 'NULL'! Queue length: " + pTwoWaySocket.GetQueue().size());
                        }
                    }
                    else {
                        logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "2. Очередь команд пустая!");
                    }
                }
                else {
                    logger.trace( pTwoWaySocket.m_pHvvComm.m_strMarker + "1. Команда испущена, а сигнала об окончнии её обработки или таймаута не было!");
                }
                
                Thread.sleep( 2);
            }
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