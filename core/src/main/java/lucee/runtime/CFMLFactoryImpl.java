/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.runtime; 

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspEngineInfo;

import lucee.aprint;
import lucee.cli.servlet.HTTPServletImpl;
import lucee.commons.io.SystemUtil;
import lucee.commons.io.log.Log;
import lucee.commons.io.log.LogUtil;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.commons.lang.SizeOf;
import lucee.commons.lang.SystemOut;
import lucee.loader.engine.CFMLEngine;
import lucee.runtime.config.ConfigImpl;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.config.ConfigWebImpl;
import lucee.runtime.config.Constants;
import lucee.runtime.engine.CFMLEngineImpl;
import lucee.runtime.engine.JspEngineInfoImpl;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.Abort;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.PageExceptionImpl;
import lucee.runtime.exp.RequestTimeoutException;
import lucee.runtime.functions.string.Hash;
import lucee.runtime.lock.LockManager;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Array;
import lucee.runtime.type.ArrayImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.dt.DateTimeImpl;
import lucee.runtime.type.scope.LocalNotSupportedScope;
import lucee.runtime.type.scope.ScopeContext;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.runtime.type.util.ListUtil;

/**
 * implements a JSP Factory, this class produce JSP Compatible PageContext Object
 * this object holds also the must interfaces to coldfusion specified functionlity
 */
public final class CFMLFactoryImpl extends CFMLFactory {
	
	private static JspEngineInfo info=new JspEngineInfoImpl("1.0");
	private ConfigWebImpl config;
	Stack<PageContext> pcs=new Stack<PageContext>();
    private final Map<Integer,PageContextImpl> runningPcs=new ConcurrentHashMap<Integer, PageContextImpl>();
    int idCounter=1;
    private ScopeContext scopeContext=new ScopeContext(this);
    private HttpServlet _servlet;
	private URL url=null;
	private CFMLEngineImpl engine;
	private ArrayList<String> cfmlExtensions;
	private ArrayList<String> luceeExtensions;
	private ServletConfig servletConfig;

	public CFMLFactoryImpl(CFMLEngineImpl engine, ServletConfig sg) {
		this.engine=engine; 
		if(engine==null)aprint.ds();
		this.servletConfig=sg;
	}
    
    /**
     * reset the PageContexes
     */
    @Override
	public void resetPageContext() {
        SystemOut.printDate(config.getOutWriter(),"Reset "+pcs.size()+" Unused PageContexts");
        synchronized(pcs) {
            pcs.clear();
        }
        Iterator<PageContextImpl> it = runningPcs.values().iterator();
        while(it.hasNext()){
        	it.next().reset();
        }
    }
    
	@Override
	public javax.servlet.jsp.PageContext getPageContext(
		Servlet servlet,
		ServletRequest req,
		ServletResponse rsp,
		String errorPageURL,
		boolean needsSession,
		int bufferSize,
		boolean autoflush) {
			return getPageContextImpl((HttpServlet)servlet,(HttpServletRequest)req,(HttpServletResponse)rsp,errorPageURL,needsSession,bufferSize,autoflush,true,false,-1,true,false);
	}
	
	@Override
	@Deprecated
	public PageContext getLuceePageContext(
	HttpServlet servlet,
	HttpServletRequest req,
	HttpServletResponse rsp,
        String errorPageURL,
		boolean needsSession,
		int bufferSize,
		boolean autoflush)  {
        //runningCount++;
        return getPageContextImpl(servlet, req, rsp, errorPageURL, needsSession, bufferSize, autoflush,true,false,-1,true,false);
	}

	@Override
	public PageContext getLuceePageContext(
	HttpServlet servlet,
	HttpServletRequest req,
	HttpServletResponse rsp,
        String errorPageURL,
		boolean needsSession,
		int bufferSize,
		boolean autoflush,boolean register, long timeout,boolean register2RunningThreads, boolean ignoreScopes)  {
        //runningCount++;
        return getPageContextImpl(servlet, req, rsp, errorPageURL, needsSession, bufferSize, autoflush,register,false,timeout,register2RunningThreads,ignoreScopes);
	}
	
	public PageContextImpl getPageContextImpl(
			HttpServlet servlet,
			HttpServletRequest req,
			HttpServletResponse rsp,
		        String errorPageURL,
				boolean needsSession,
				int bufferSize,
				boolean autoflush,boolean register2Thread,boolean isChild,long timeout,boolean register2RunningThreads,boolean ignoreScopes) {
		        PageContextImpl pc; 
				synchronized (pcs) {
					if(pcs.isEmpty()) pc=new PageContextImpl(scopeContext,config,idCounter++,servlet);
		            else pc=((PageContextImpl)pcs.pop());
		            if(timeout>0)pc.setRequestTimeout(timeout);
		            if(register2RunningThreads)runningPcs.put(Integer.valueOf(pc.getId()),pc);
		            this._servlet=servlet;
		            if(register2Thread)ThreadLocalPageContext.register(pc);
		    		
		        }
		        pc.initialize(servlet,req,rsp,errorPageURL,needsSession,bufferSize,autoflush,isChild,ignoreScopes);
		        return pc;
			}

    @Override
	public void releasePageContext(javax.servlet.jsp.PageContext pc) {
		releaseLuceePageContext((PageContext)pc);
	}

    @Override
	public CFMLEngine getEngine() {
		return engine;
	}
    
    @Override
	@Deprecated
	public void releaseLuceePageContext(PageContext pc) {
		releaseLuceePageContext(pc, true);
	}
	
	/**
	 * Similar to the releasePageContext Method, but take lucee PageContext as entry
	 * @param pc
	 */
	@Override
	public void releaseLuceePageContext(PageContext pc, boolean register) {
		if(pc.getId()<0)return;
        pc.release();
        ThreadLocalPageContext.release();
        //if(!pc.hasFamily()){
			    runningPcs.remove(Integer.valueOf(pc.getId()));
			    if(pcs.size()<100 && pc.getRequestTimeoutException()==null)// not more than 100 PCs
			    	pcs.push(pc);
	            //SystemOut.printDate(config.getOutWriter(),"Release: (id:"+pc.getId()+";running-requests:"+config.getThreadQueue().size()+";)");
	        
       /*}
        else {
        	 SystemOut.printDate(config.getOutWriter(),"Unlink: ("+pc.getId()+")");
        }*/
	}
    
    /**
	 * check timeout of all running threads, downgrade also priority from all thread run longer than 10 seconds
	 */
	@Override
	public void checkTimeout() {
		if(!engine.allowRequestTimeout())return;
		//synchronized (runningPcs) {
            //int len=runningPcs.size();
			Iterator<Entry<Integer, PageContextImpl>> it = runningPcs.entrySet().iterator();
            PageContextImpl pc;
            //Collection.Key key;
            Entry<Integer, PageContextImpl> e;
            while(it.hasNext()) {
            	e = it.next();
            	pc=e.getValue();
                
                long timeout=pc.getRequestTimeout();
                if(pc.getStartTime()+timeout<System.currentTimeMillis()) {
                	terminate(pc);
                    it.remove();
                }
                // after 10 seconds downgrade priority of the thread
                else if(pc.getStartTime()+10000<System.currentTimeMillis() && pc.getThread().getPriority()!=Thread.MIN_PRIORITY) {
                    Log log = config.getLog("requesttimeout");
                    if(log!=null)log.log(Log.LEVEL_WARN,"controler","downgrade priority of the a thread at "+getPath(pc));
                    try {
                    	pc.getThread().setPriority(Thread.MIN_PRIORITY);
                    }
                    catch(Throwable t) {}
                }
            }
        //}
	}
	
	public static void terminate(PageContextImpl pc) {
		Log log = ((ConfigImpl)pc.getConfig()).getLog("requesttimeout");
        
		String strLocks="";
		try{
			LockManager manager = pc.getConfig().getLockManager();
	        String[] locks = manager.getOpenLockNames();
	        if(!ArrayUtil.isEmpty(locks)) 
	        	strLocks=" open locks at this time ("+ListUtil.arrayToList(locks, ", ")+").";
	        //LockManagerImpl.unlockAll(pc.getId());
		}
		catch(Throwable t){}
        if(log!=null)LogUtil.log(log,Log.LEVEL_ERROR,"controler",
        		"stop thread ("+pc.getId()+") because run into a timeout "+getPath(pc)+"."+strLocks,pc.getThread().getStackTrace());
        pc.getConfig().getThreadQueue().exit(pc);
        SystemUtil.stop(pc,new RequestTimeoutException(pc.getThread(),"request ("+getPath(pc)+":"+pc.getId()+") has run into a timeout ("+(pc.getRequestTimeout()/1000)+" seconds) and has been stopped."+strLocks),log);

	}

	private static String getPath(PageContext pc) {
		try {
			String base=ResourceUtil.getResource(pc, pc.getBasePageSource()).getAbsolutePath();
			String current=ResourceUtil.getResource(pc, pc.getCurrentPageSource()).getAbsolutePath();
			if(base.equals(current)) return "path: "+base;
			return "path: "+base+" ("+current+")";
		}
		catch(Throwable t) {
			return "(fail to retrieve path:"+t.getClass().getName()+":"+t.getMessage()+")";
		}
	}
	
	@Override
	public JspEngineInfo getEngineInfo() {
		return info;
	}


	/**
	 * @return returns count of pagecontext in use
	 */
	@Override
	public int getUsedPageContextLength() { 
		int length=0;
		try{
		Iterator it = runningPcs.values().iterator();
		while(it.hasNext()){
			PageContextImpl pc=(PageContextImpl) it.next();
			if(!pc.isGatewayContext()) length++;
		}
		}
		catch(Throwable t){
			return length;
		}
	    return length;
	}
    /**
     * @return Returns the config.
     */
    @Override
	public ConfigWeb getConfig() {
        return config;
    }
    public ConfigWebImpl getConfigWebImpl() {
        return config;
    }
    /**
     * @return Returns the scopeContext.
     */
    public ScopeContext getScopeContext() {
        return scopeContext;
    }

    /**
     * @return label of the factory
     */
    @Override
	public Object getLabel() {
    	return ((ConfigWebImpl)getConfig()).getLabel();
    }
    /**
     * @param label
     */
    @Override
	public void setLabel(String label) {
        // deprecated
    }

    @Override
	public URL getURL() {
		return url;
	}
    

	public void setURL(URL url) {
		this.url=url;
	}

	/**
	 * @return the servlet
	 */
	@Override
	public HttpServlet getServlet() {
		if(_servlet==null)_servlet=new HTTPServletImpl(servletConfig, servletConfig.getServletContext(), servletConfig.getServletName());;
		return _servlet;
	}

	public void setConfig(ConfigWebImpl config) {
		this.config=config;
	}

	public Map<Integer, PageContextImpl> getActivePageContexts() {
		return runningPcs;
	}
	
	public long getPageContextsSize() {
		return SizeOf.size(pcs);
	}
	
	public Array getInfo() {
		Array info=new ArrayImpl();
		
		//synchronized (runningPcs) {
            //int len=runningPcs.size();
			Iterator<PageContextImpl> it = runningPcs.values().iterator();
            PageContextImpl pc;
            Struct data,sctThread,scopes;
    		Thread thread;
    		Entry<Integer, PageContextImpl> e;
    		while(it.hasNext()) {
    			pc = it.next();
            	data=new StructImpl();
            	sctThread=new StructImpl();
            	scopes=new StructImpl();
            	data.setEL("thread", sctThread);
                data.setEL("scopes", scopes);
                
            	if(pc.isGatewayContext()) continue;
                thread=pc.getThread();
                if(thread==Thread.currentThread()) continue;

                
                thread=pc.getThread();
                if(thread==Thread.currentThread()) continue;
                
               
                
                data.setEL("startTime", new DateTimeImpl(pc.getStartTime(),false));
                data.setEL("endTime", new DateTimeImpl(pc.getStartTime()+pc.getRequestTimeout(),false));
                data.setEL(KeyConstants._timeout,new Double(pc.getRequestTimeout()));

                
                // thread
                sctThread.setEL(KeyConstants._name,thread.getName());
                sctThread.setEL("priority",Caster.toDouble(thread.getPriority()));
                data.setEL("TagContext",PageExceptionImpl.getTagContext(pc.getConfig(),thread.getStackTrace() ));

                data.setEL("urlToken", pc.getURLToken());
                try {
					if(pc.getConfig().debug())data.setEL("debugger", pc.getDebugger().getDebuggingData(pc));
				} catch (PageException e2) {}

                try {
					data.setEL("id", Hash.call(pc, pc.getId()+":"+pc.getStartTime()));
				} catch (PageException e1) {}
                data.setEL("requestid", pc.getId());

                // Scopes
                scopes.setEL(KeyConstants._name, pc.getApplicationContext().getName());
                try {
					scopes.setEL(KeyConstants._application, pc.applicationScope());
				} catch (PageException pe) {}

                try {
					scopes.setEL(KeyConstants._session, pc.sessionScope());
				} catch (PageException pe) {}
                
                try {
					scopes.setEL(KeyConstants._client, pc.clientScope());
				} catch (PageException pe) {}
                scopes.setEL(KeyConstants._cookie, pc.cookieScope());
                scopes.setEL(KeyConstants._variables, pc.variablesScope());
                if(!(pc.localScope() instanceof LocalNotSupportedScope)){
                	scopes.setEL(KeyConstants._local, pc.localScope());
                	scopes.setEL(KeyConstants._arguments, pc.argumentsScope());
                }
                scopes.setEL(KeyConstants._cgi, pc.cgiScope());
                scopes.setEL(KeyConstants._form, pc.formScope());
                scopes.setEL(KeyConstants._url, pc.urlScope());
                scopes.setEL(KeyConstants._request, pc.requestScope());
                
                info.appendEL(data);
            }
            return info;
        //}
	}

	public void stopThread(String threadId, String stopType) {
		//synchronized (runningPcs) {
            Iterator<PageContextImpl> it = runningPcs.values().iterator();
            PageContext pc;
    		while(it.hasNext()) {
            	pc=it.next();
                Log log = ((ConfigImpl)pc.getConfig()).getLog("application");
                try {
					String id = Hash.call(pc, pc.getId()+":"+pc.getStartTime());
					if(id.equals(threadId)){
						stopType=stopType.trim();
						Throwable t;
						if("abort".equalsIgnoreCase(stopType) || "cfabort".equalsIgnoreCase(stopType))
							t=new Abort(Abort.SCOPE_REQUEST);
						else
							t=new RequestTimeoutException(pc.getThread(),"request has been forced to stop.");
						
						SystemUtil.stop(pc,t,log);
		                SystemUtil.sleep(10);
						break;
					}
				} catch (PageException e1) {}
                
            }
        //}
	}

	@Override
	public JspApplicationContext getJspApplicationContext(ServletContext arg0) {
		throw new RuntimeException("not supported!");
	}

	@Override
	public int toDialect(String ext) {
		if(cfmlExtensions==null) _initExtensions();
		if(cfmlExtensions.contains(ext.toLowerCase())) return CFMLEngine.DIALECT_CFML;
		return CFMLEngine.DIALECT_LUCEE;
	}


	
	private void _initExtensions() {
		cfmlExtensions=new ArrayList<String>();
		luceeExtensions=new ArrayList<String>();
		try {
			
			Iterator<?> it = getServlet().getServletContext().getServletRegistrations().entrySet().iterator();
			Entry<String,? extends ServletRegistration> e;
			String cn;
			while(it.hasNext()){
	    		e = (Entry<String, ? extends ServletRegistration>) it.next();
	    		cn=e.getValue().getClassName();
	    		
	    		if(cn!=null && cn.indexOf("LuceeServlet")!=-1) {
	    			setExtensions(luceeExtensions,e.getValue().getMappings().iterator());
	    		}
	    		else if(cn!=null && cn.indexOf("CFMLServlet")!=-1) {
					setExtensions(cfmlExtensions,e.getValue().getMappings().iterator());
				}
	    	}
		}
		catch(Throwable t){
			ArrayUtil.addAll(cfmlExtensions,Constants.getCFMLExtensions());
			ArrayUtil.addAll(luceeExtensions,Constants.getLuceeExtensions());
		}
	}

	private void setExtensions(ArrayList<String> extensions, Iterator<String> it) {
		String str,str2;
		Iterator<String> it2;
		while(it.hasNext()){
			str=it.next();
			it2=ListUtil.listToSet(str, ',', true).iterator();
			while(it2.hasNext()){
				str2=it2.next();
				extensions.add(str2.substring(2));// MUSTMUST better impl
			}
		}
	}

	@Override
	public Iterator<String> getCFMLExtensions() {
		if(cfmlExtensions==null) _initExtensions();
		return cfmlExtensions.iterator();
	}
	

	@Override
	public Iterator<String> getLuceeExtensions() {
		if(luceeExtensions==null) _initExtensions();
		return luceeExtensions.iterator();
	}
}