package zz;

// -----( IS Java Code Template v1.2

import com.wm.data.*;
import com.wm.util.Values;
import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.ServiceException;
// --- <<IS-START-IMPORTS>> ---
// --- <<IS-END-IMPORTS>> ---

public final class licmon

{
	// ---( internal utility methods )---

	final static licmon _instance = new licmon();

	static licmon _newInstance() { return new licmon(); }

	static licmon _cast(Object o) { return (licmon)o; }

	// ---( server methods )---




	public static final void epochToDate (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(epochToDate)>> ---
		// @sigtype java 3.5
		// [i] field:0:required epochMillis
		// [o] object:0:required convertedDate
		// pipeline
		IDataCursor pipelineCursor = pipeline.getCursor();
			String	epochMillis = IDataUtil.getString( pipelineCursor, "epochMillis" );
		pipelineCursor.destroy();
		
		long millis = Long.parseLong(epochMillis); 
		
		// pipeline
		IDataCursor pipelineCursor_1 = pipeline.getCursor();
		Object convertedDate = new Object();
		IDataUtil.put( pipelineCursor_1, "convertedDate", new java.util.Date(millis) );
		pipelineCursor_1.destroy();
		// --- <<IS-END>> ---

                
	}



	public static final void getMetrics (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(getMetrics)>> ---
		// @sigtype java 3.5
		// [o] record:1:required metrics
		// [o] - field:0:required serviceNs
		// [o] - field:0:required invokeCount
		// [o] - field:0:required transactionIntervalsCount
		// [o] - field:0:required maxDurationMillis
		// [o] - field:0:required avgSecondDuration
		// [o] - field:1:required histogram
		com.ibm.tel.wm.LicenseMonitor lmi = com.ibm.tel.wm.LicenseMonitor.getInstance();
		String serviceNSArray[] = lmi.getServiceNamespaces();
		// pipeline
		IDataCursor pipelineCursor = pipeline.getCursor();
		
		com.ibm.tel.wm.ServiceMetrics sm = null;
		
		// metrics
		IData[]	metrics = new IData[serviceNSArray.length];
		for (int i=0; i<serviceNSArray.length; i++){
			sm = lmi.getMetrics(serviceNSArray[i]);
			metrics[i] = IDataFactory.create();
			IDataCursor metricsCursor = metrics[i].getCursor();
			IDataUtil.put( metricsCursor, "serviceNs", serviceNSArray[i] );
			IDataUtil.put( metricsCursor, "invokeCount", "" + sm.getInvokeCount() );
			IDataUtil.put( metricsCursor, "transactionIntervalsCount", "" + sm.getTransactionIntervalsCount() );
			IDataUtil.put( metricsCursor, "maxDurationMillis", "" + sm.getMaxDurationMillis() );
			IDataUtil.put( metricsCursor, "avgSecondDuration", String.format("%.2f", sm.getAvgSecondDuration()) );
			
			long[] h=sm.getHistogramArray();
			String[] histogram = new String[h.length];
			for(int j=0; j<h.length; j++){
				histogram[j] = "" + h[j];
			};
			
			IDataUtil.put( metricsCursor, "histogram", histogram );
			
			metricsCursor.destroy();
		}
		IDataUtil.put( pipelineCursor, "metrics", metrics );
		pipelineCursor.destroy();
		// --- <<IS-END>> ---

                
	}



	public static final void getUptimeMillis (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(getUptimeMillis)>> ---
		// @sigtype java 3.5
		// [o] field:0:required startTimeEpoch
		// pipeline
		
		IDataCursor pipelineCursor = pipeline.getCursor();
		IDataUtil.put( pipelineCursor, "startTimeEpoch", ""+com.ibm.tel.wm.LicenseMonitor.getInitMillis() );
		pipelineCursor.destroy();
		// --- <<IS-END>> ---

                
	}



	public static final void startup (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(startup)>> ---
		// @sigtype java 3.5
		com.ibm.tel.wm.InvokeChainInterceptor i = com.ibm.tel.wm.InvokeChainInterceptor.INSTANCE;
		// --- <<IS-END>> ---

                
	}



	public static final void writeCsvToFile (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(writeCsvToFile)>> ---
		// @sigtype java 3.5
		// [i] field:0:required filePathAndName
		com.ibm.tel.wm.LicenseMonitor lm = com.ibm.tel.wm.LicenseMonitor.getInstance();
		
		// pipeline
		IDataCursor pipelineCursor = pipeline.getCursor();
			String	filePathAndName = IDataUtil.getString( pipelineCursor, "filePathAndName" );
		pipelineCursor.destroy();
		
		try{
			lm.exportToCSVFile(filePathAndName);
		}catch(Throwable t){
			throw new ServiceException(t);
		}
		
		
		// pipeline
		// --- <<IS-END>> ---

                
	}



	public static final void writeCsvToFolder (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(writeCsvToFolder)>> ---
		// @sigtype java 3.5
		// [i] field:0:required folderPath
		com.ibm.tel.wm.LicenseMonitor lm = com.ibm.tel.wm.LicenseMonitor.getInstance();
		
		// pipeline
		IDataCursor pipelineCursor = pipeline.getCursor();
			String	folderPath = IDataUtil.getString( pipelineCursor, "folderPath" );
		pipelineCursor.destroy();
		
		try{
			lm.exportToCSVFileInFolder(folderPath);
		}catch(Throwable t){
			throw new ServiceException(t);
		}
		
		
		// pipeline
		// --- <<IS-END>> ---

                
	}
}

