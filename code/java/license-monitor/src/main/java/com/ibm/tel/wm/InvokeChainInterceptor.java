package com.ibm.tel.wm;
import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.invoke.InvokeChainProcessor;
import com.wm.app.b2b.server.invoke.InvokeManager;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.util.ServerException;
import java.util.Iterator;

public class InvokeChainInterceptor implements InvokeChainProcessor {
	public static final InvokeChainInterceptor INSTANCE = new InvokeChainInterceptor();

	static {
    InvokeManager.getDefault().registerProcessor(INSTANCE);
    System.out.println("License monitor processor registered");
	}
	
	public InvokeChainInterceptor() {
	}

	@Override
	public void process(@SuppressWarnings("rawtypes") Iterator chain
                      , BaseService svc, IData pipeline, ServiceStatus status) 
                      throws ServerException {
    if (!status.isTopService()) {
			if (chain.hasNext()) {
				((InvokeChainProcessor) chain.next()).process(chain, svc, pipeline, status);
			}
      return; // If not a top level service, just pass the ball down the chain
    }

    long startTime = System.currentTimeMillis();
		String serviceNS = svc.getNSName().getFullName();

    try {
      if (chain.hasNext()) {
        ((InvokeChainProcessor) chain.next()).process(chain, svc, pipeline, status);
      }
    } finally {
      long duration = System.currentTimeMillis() - startTime;

      // count one transaction per invoke plus one transaction for every supplemental 30 seconds
      long transactionsNumber = svc.getPackage().isSystemPackage() ? 0 : 1 + (duration / 30000);
      LicenseMonitor.getInstance().incrementMetrics(serviceNS, 1, transactionsNumber);
    }
	}
}
