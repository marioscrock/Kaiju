package eps;

import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPStatement;

import eps.listener.CEPListenerHL;
import eps.listener.CEPTailSamplingListener;

public class EsperStatements {
	
	/**
	 * Define a named window storing traceId of incoming spans {@code (traceIdHex string)}.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param retentionTime Sliding time of the window.
	 */
	public static void defineTracesWindow(EPAdministrator cepAdm, String retentionTime) {
		cepAdm.createEPL("create window TracesWindow#unique(traceIdHex)#time(" + retentionTime + ") (traceIdHex string)");
	    cepAdm.createEPL("insert into TracesWindow(traceIdHex)"
	    		+ " select eps.EventToJsonConverter.traceIdToHex(traceIdHigh, traceIdLow)"
	    		+ " from Span");		
	}

	/**
	 * Define a table storing processes of incoming batches {@code (hashProcess int primary key, process thriftgen.Process, hostname string)}.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 */
	public static void defineProcessesTable(EPAdministrator cepAdm) {
		cepAdm.createEPL("create table ProcessesTable (hashProcess int primary key, process thriftgen.Process, hostname string)");
	    cepAdm.createEPL("on Batch b merge ProcessesTable p"
	    		+ " where eps.EventToJsonConverter.hashProcess(b.process) = p.hashProcess"
	    		+ " when not matched then insert select eps.EventToJsonConverter.hashProcess(process) as hashProcess, process,"
	    		+ " process.tags.firstOf(t => t.key = 'hostname').getVStr() as hostname");	
	}
	
	/**
	 * Define a named window storing incoming spans and the related process {@code (span thriftgen.Span, hashProcess int, serviceName string)}.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param retentionTime Sliding time of the window.
	 */
	public static void defineSpansWindow(EPAdministrator cepAdm, String retentionTime) {
		cepAdm.createEPL("create window SpansWindow#time(" + retentionTime + ") as (span thriftgen.Span, hashProcess int, serviceName string)");
		cepAdm.createEPL("insert into SpansWindow"
		    		+ " select s as span, eps.EventToJsonConverter.hashProcess(p) as hashProcess, p.serviceName as serviceName"
		    		+ " from Batch[select process as p, * from spans as s]");	 
		
	}
	
	/**
	 * Define a named window storing dependencies of incoming spans {@code (traceIdHexFrom string, spanIdFrom long, traceIdHexTo string, spanIdTo long)}
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param retentionTime Sliding time of the window.
	 */
	public static void defineDependenciesWindow(EPAdministrator cepAdm, String retentionTime) {
		cepAdm.createEPL("create window DependenciesWindow#time(" + retentionTime + ") (traceIdHexFrom string,"
	    		+ " spanIdFrom long, traceIdHexTo string, spanIdTo long)");
	    cepAdm.createEPL("insert into DependenciesWindow"
	    		+ " select eps.EventToJsonConverter.traceIdToHex(s.span.traceIdHigh, s.span.traceIdLow) as traceIdHexTo,"
	    		+ " s.span.spanId as spanIdTo,"
	    		+ " eps.EventToJsonConverter.traceIdToHex(s.r.traceIdHigh, s.r.traceIdLow) as traceIdHexFrom,"
	    		+ " s.r.spanId as spanIdFrom"
	    		+ " from SpansWindow[select span.spanId, span.traceIdLow, span.traceIdHigh,* from span.references as r] s");	
	}
	
	/**
	 * Define a named window storing traceIds of traces to be saved {@code (traceId string)} and all the rules 
	 * to populate the window.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param retentionTime Sliding time of the window.
	 */
	public static void defineTracesToBeSampledWindow(EPAdministrator cepAdm, String retentionTime) {
		cepAdm.createEPL("create window TracesToBeSampledWindow#unique(traceId)#time(" + retentionTime + ") (traceId string)");
	    cepAdm.createEPL("on TraceAnomaly a"
	    		+ " merge TracesToBeSampledWindow t"
	    		+ " where a.traceId = t.traceId"
	    		+ " when not matched"
	    		+ " then insert into TracesToBeSampledWindow select a.traceId as traceId");
	}
	
	/**
	 * Register a {@link eps.listener.CEPTailSamplingListener CEPTailSamplingListener} saving
	 * spans exiting from {@code SpansWindow} to file.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param filepath Filepath of the file to save spans.
	 */
	public static void tailSampling(EPAdministrator cepAdm, String filepath) {
	    EPStatement tailSampling = cepAdm.createEPL("select rstream * from SpansWindow as s"
	    		+ " where exists (select * from TracesToBeSampledWindow "
	    		+ "where traceId = (eps.EventToJsonConverter.traceIdToHex(s.span.traceIdHigh, s.span.traceIdLow)))");
	    tailSampling.addListener(new CEPTailSamplingListener(filepath)); 		
	}
	
	/**
	 * Default method attaching forwarding listener to {@link eventsocket.Event Event}.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 */
	public static void reportHLEvents(EPAdministrator cepAdm) {
		EPStatement hlEvent = cepAdm.createEPL("select * from Event");
		hlEvent.addListener(new CEPListenerHL("kaiju-hl"));
	}

	/**
	 * Default statements for Traces mode.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 */
	public static void defaultStatementsTraces(EPAdministrator cepAdm) {
		
		EsperStatements.reportHLEvents(cepAdm);
		
		if (EsperHandler.config.mode.equals("traces-api")) {
			String retentionTime = EsperHandler.config.retentionTime;
			defineTracesWindow(cepAdm, retentionTime);
			defineProcessesTable(cepAdm);
			defineSpansWindow(cepAdm, retentionTime);
			defineDependenciesWindow(cepAdm, retentionTime);
		}
		
	}

	/**
	 * Default statements for Metrics mode.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 */
	public static void defaultStatementsMetrics(EPAdministrator cepAdm) {
		
//		DEBUG STATEMENT
//	    EPStatement cepMetrics = cepAdm.createEPL("select * from Metric"); 
//	    cepMetrics.addListener(new CEPListener("Metric: "));	
		
		EsperStatements.reportHLEvents(cepAdm);
	    
	}

	/**
	 * Default statements for Logs mode.
	 * @param cepAdm {@link com.espertech.esper.client.EPAdministrator EPAdministrator} of the Esper engine.
	 * @param retentionTime Retention time of Esper.
	 */
	public static void defaultStatementsLogs(EPAdministrator cepAdm) {
		
//		DEBUG STATEMENT
//		EPStatement cepFLogs = cepAdm.createEPL("select * from FLog"); 
//	    cepFLogs.addListener(new CEPListener("FLog: "));
		
		EsperStatements.reportHLEvents(cepAdm);
	 
	}


}
