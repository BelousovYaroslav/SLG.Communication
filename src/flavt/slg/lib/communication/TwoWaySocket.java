package flavt.slg.lib.communication;


import HVV_Communication.CommandItem;
import hvv_timeouts.HVV_TimeoutsManager;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

public class TwoWaySocket
{
    static Logger logger = Logger.getLogger(TwoWaySocket.class);
    
    public Thread thrInput;
    public Thread thrOutput;
            
    Socket m_pSocket;
    
    volatile public long m_lTimeOutId;
    
    volatile int m_nTimeoutCounter;
    volatile private CommandItem m_pCommandInAction;

    synchronized CommandItem GetCmdInAction() { return m_pCommandInAction; }
    synchronized void SetCmdInAction( CommandItem pNewAction) { m_pCommandInAction = pNewAction; }

    
    int m_nCmdCounter;
    
    SocketReader m_sr;
    SocketWriter m_sw;
    
    private String m_strHostConnectTo;
    private int m_nPortConnectTo;
    
    /* ********************************************************** */
    /* *************** COMMAND QUEUE **************************** */
    private final ConcurrentLinkedQueue cmdQueue;
    
    public synchronized ConcurrentLinkedQueue GetQueue() { return cmdQueue; }
    
    public synchronized void AddCommandToQueue( CommandItem item) {
        item.SetCommandId( "" + m_nCmdCounter);
        cmdQueue.add( item);
        
        logger.debug( m_pHvvComm.m_strMarker + "AddCommandToQueue(" + item + ", " + m_nCmdCounter + "): queue length: " + cmdQueue.size());
        
        m_nCmdCounter = ( ++m_nCmdCounter) % 254;
    }
    
    /* ********************************************************** */
    
    
    public SLG_Comm_client m_pHvvComm;
            
    public TwoWaySocket( String strHostConnectTo, int nPortConnectTo, SLG_Comm_client pParent)
    {
        cmdQueue = new ConcurrentLinkedQueue();        
        m_pSocket = null;
        m_nCmdCounter = 0;
        m_pHvvComm = pParent;
        
        m_strHostConnectTo = strHostConnectTo;
        m_nPortConnectTo = nPortConnectTo;
    }
    
    public boolean connect( /*COMPortSettings pSettings*/) throws Exception
    {
        if( m_pSocket != null && !m_pSocket.isClosed()) {
            logger.error( m_pHvvComm.m_strMarker + "Socket is already open!");
            return false;
        }
        
        logger.debug( m_pHvvComm.m_strMarker + "Connecting to " + m_strHostConnectTo + ":" + m_nPortConnectTo);
        
        try {
            m_pSocket = new Socket( InetAddress.getByName( m_strHostConnectTo), m_nPortConnectTo);
        }
        catch( SocketException ex) {
            //logger.info( "SocketException caught", ex);
            logger.info( m_pHvvComm.m_strMarker + "SocketException: " + ex.getLocalizedMessage());
            m_pSocket = null;
            return false;
        }
        
        InputStream is = m_pSocket.getInputStream();
        ObjectInputStream ois = new ObjectInputStream( m_pSocket.getInputStream());
        ObjectOutputStream out = new ObjectOutputStream( m_pSocket.getOutputStream());
                
        m_sr = new SocketReader( is, ois, this);
        thrInput = new Thread( m_sr);
        thrInput.start();
        
        m_sw = new SocketWriter( out, this);
        thrOutput = new Thread( m_sw);
        thrOutput.start();
        
        return true;
    }
    
    public boolean IsConnected() {
        if( m_pSocket == null) return false;
        //logger.debug( "Socket isConnected:" + pSocket.isConnected());
        return m_pSocket.isConnected();
    }
    
    public void disconnect( /*COMPortSettings pSettings*/) throws Exception {
        //if( pTimeoutThread != null)
        //    pTimeoutThread.interrupt();
        
        if( m_lTimeOutId != 0) {            
            HVV_TimeoutsManager.getInstance().RemoveId( m_lTimeOutId);
            m_lTimeOutId = 0;
        }
        
        if( m_sr != null) {
            m_sr.StopThread();
            thrInput.join();
            thrInput = null;
            m_sr = null;
        }
        
        if( m_sw != null) {
            m_sw.StopThread();
            thrOutput.join();
            thrOutput = null;
            m_sw = null;
        }
        
        if( m_pSocket != null) {
            m_pSocket.close();
            m_pSocket = null;
        }
        
        logger.fatal( "**** **** **** **** **** QUEUE CLEARED!");
        cmdQueue.clear();
        
        if( m_pCommandInAction != null) {
            m_pCommandInAction.GetParcelIn().clear();
            m_pCommandInAction.GetParcelOut().clear();
            m_pCommandInAction = null;
        }
    }
}