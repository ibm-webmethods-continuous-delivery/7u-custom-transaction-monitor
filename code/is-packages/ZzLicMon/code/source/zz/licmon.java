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




	public static final void getMetrics (IData pipeline)
        throws ServiceException
	{
		// --- <<IS-START(getMetrics)>> ---
		// @sigtype java 3.5
		// [o] record:1:required metrics
		// [o] - field:0:required serviceNs
		// [o] - field:0:required invokeCount
		// [o] - field:0:required transactionCount
		com.ibm.tel.wm.LicenseMonitor lmi = com.ibm.tel.wm.LicenseMonitor.getInstance();
		String serviceNSArray[] = lmi.getServiceNamespaces();
		// pipeline
		IDataCursor pipelineCursor = pipeline.getCursor();
		
		com.ibm.tel.wm.ServiceMetrics sm = null;
		
		// metrics
		IData[]	metrics = new IData[serviceNSArray.length];
		System.out.println("Called..., number of registered services: serviceNSArray.length");
		for (int i=0; i<serviceNSArray.length; i++){
			sm = lmi.getMetrics(serviceNSArray[i]);
			metrics[i] = IDataFactory.create();
			IDataCursor metricsCursor = metrics[i].getCursor();
			IDataUtil.put( metricsCursor, "serviceNs", serviceNSArray[i] );
			IDataUtil.put( metricsCursor, "invokeCount", sm.getInvokeCount() );
			IDataUtil.put( metricsCursor, "transactionCount", sm.getTransactionCount() );
			metricsCursor.destroy();
		}
		IDataUtil.put( pipelineCursor, "metrics", metrics );
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
}

