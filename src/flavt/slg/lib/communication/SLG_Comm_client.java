/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package flavt.slg.lib.communication;

import HVV_Communication.CommandItem;
import HVV_Communication.executors.PingRunnable;
import static java.lang.Thread.sleep;
import java.util.LinkedList;
import org.apache.log4j.Logger;


/**
 *
 * @author yaroslav
 */
public class SLG_Comm_client implements Runnable {
    
    TwoWaySocket m_rxtx;
    public TwoWaySocket GetRxTx() { return m_rxtx; }
            
    static Logger logger = Logger.getLogger(SLG_Comm_client.class);
    
    private int m_nState;
    public int GetState() { return m_nState; }
    public void SetState( int nState) { m_nState = nState; }
    
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED_OK = 1;
    public static final int STATE_CONNECTED_PROBLEMS = 2;
    public static final int STATE_CONNECTED_IDLE = 3;
    
    public Thread m_Thread;
    boolean m_bContinue;
    
    public boolean m_bStopRequested;
    public boolean m_bServerStopRequested;
    
    public final String m_strMarker;
    
    public SLG_Comm_client( String strMarker, String strHostConnectTo, int nPortConnectTo) {
        m_strMarker = strMarker;
        m_rxtx = new TwoWaySocket( strHostConnectTo, nPortConnectTo, this);
        m_Thread = null;
        m_nState = STATE_DISCONNECTED;
    }
    
    public void start() {
        if( m_Thread != null && m_Thread.isAlive() == true)
            return;
        
        m_Thread = new Thread( this);
        m_Thread.start();
    }
    
    public void stop( boolean bJoin) {
        if( m_Thread != null) {
            try {
                if( m_nState != STATE_DISCONNECTED) {
                    m_bStopRequested = true;

                    LinkedList lstQuitCmd = new LinkedList();
                    lstQuitCmd.addLast( "QUIT");
                    CommandItem quitItem = new CommandItem( null, lstQuitCmd);
                    m_rxtx.AddCommandToQueue( quitItem);

                    if( bJoin)
                        m_Thread.join();

                    m_nState = STATE_DISCONNECTED;

                    m_Thread = null;
                }
                else {
                    m_bContinue = false;
                    m_Thread.join();
                    m_nState = STATE_DISCONNECTED;
                    m_Thread = null;
                }
            } catch( InterruptedException ex) {
                logger.warn( m_strMarker + "InterruptedException caught!", ex);
            }
        }
    }

    @Override
    public void run() {
        m_bContinue = true;
        
        PingRunnable ping = new PingRunnable( this);
        Thread pingThread = new Thread( ping);
        
        do {
            logger.trace( m_strMarker + "HVV_Comm thread cycle in...");
            if( m_rxtx.IsConnected()) {
                //мы подсоединены - всё ок
                m_nState = STATE_CONNECTED_OK;
                
                if( m_rxtx != null &&
                            m_rxtx.thrInput != null && m_rxtx.thrOutput != null &&
                            m_rxtx.thrInput.isAlive() && m_rxtx.thrOutput.isAlive()) {
                    
                    logger.trace( m_strMarker + "HVV_Comm thread is alive, connected, and both reader and writer are running!");
                    
                    
                    
                    try {
                        sleep( 100);
                    } catch (InterruptedException ex) {
                        logger.warn( m_strMarker + "InterruptedException caught!", ex);
                    }
                            
                }
                else {
                    if( m_rxtx == null) logger.warn( m_strMarker + "m_rxtx == null");
                    
                    if( m_rxtx.thrInput == null || m_rxtx.thrInput.isAlive() == false)
                        logger.warn( m_strMarker + "m_rxtx.thrInput problems");
                    
                    if( m_rxtx.thrOutput == null || m_rxtx.thrOutput.isAlive() == false)
                        logger.warn( m_strMarker + "m_rxtx.thrOutput problems");
                    
                    if( m_bStopRequested)
                        logger.warn( m_strMarker + "Processing 'QUIT'!");
                    else if( m_bServerStopRequested)
                        logger.warn( m_strMarker + "Processing 'SERVER QUIT'!");
                    else
                        logger.warn( m_strMarker + "Connection broken! Disconnecting!");
                    
                    ping.StopThread();
                    
                    try {
                        pingThread.join();
                    } catch( InterruptedException ex) {
                        logger.error( m_strMarker + "InterruptedException caught while stopping ping thread!", ex);
                    }
        
                    try {
                        m_rxtx.disconnect();
                    } catch( Exception ex) {
                        logger.warn( m_strMarker + "Exception caught on disconnecting!", ex);
                    }
                    
                    m_nState = STATE_DISCONNECTED;
                    
                    if( m_bStopRequested == true)
                        m_bContinue = false;
                }
            }
            else {
                //мы не подсоединены... подсоединяемся
                m_nState = STATE_DISCONNECTED;
                
                if( m_bServerStopRequested) {
                    
                    try {
                        logger.info( "Был запрос от сервера на остановку - мы отсоединились, теперь дадим ему 3 секунды на закрытие сервер-сокета.");
                        sleep( 3000);
                    } catch( InterruptedException ex) {
                        logger.error( "InterruptedException caught!", ex);
                    }
                    
                    m_bServerStopRequested = false;
                    
                }
                if( m_bStopRequested == true)
                     break;
                
                try {
                    m_rxtx.connect();
                } catch( Exception ex) {
                    logger.warn( m_strMarker + "Exception caught!", ex);
                }
            
                if( m_rxtx.IsConnected() == false) {
                    logger.warn( m_strMarker + "Попытка соединиться неуспешна.");
                    try {
                        sleep( 5000);
                    } catch (InterruptedException ex) {
                        logger.warn( m_strMarker + "InterruptedException caught!", ex);
                    }
                }
                else {
                    logger.warn( m_strMarker + "Соединение установлено!");
                    
                    m_bStopRequested = false;
                    m_bServerStopRequested = false;
                    
                    m_nState = STATE_CONNECTED_OK;
                    pingThread = new Thread( ping);
                    pingThread.start();
                }
                
                
            }
            
        } while( m_bContinue);
        
        try {
            m_rxtx.disconnect();
        } catch( Exception ex) {
            logger.warn( m_strMarker + "Exception caught!", ex);
        }
    }
}
