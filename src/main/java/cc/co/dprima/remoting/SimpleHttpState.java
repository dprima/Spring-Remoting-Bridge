package cc.co.dprima.remoting;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.auth.AuthScope;

/**
 * @author dprima
 * 
 */
public class SimpleHttpState extends HttpState {

	/**
	 * Set credentials property.
	 * 
	 * @param credentials
	 * @see <ul>
	 *      <li>setCredentials(org.apache.commons.httpclient.auth.AuthScope,
	 *      org.apache.commons.httpclient.Credentials)</li>
	 *      <li>{@link http
	 *      ://stackoverflow.com/questions/4615039/spring-security
	 *      -authentication-using-resttemplate}</li>
	 *      </ul>
	 */
	public void setCredentials(final Credentials credentials) {
		super.setCredentials(AuthScope.ANY, credentials);
	}

}
