package io.gh.dprimax.remoting;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.remoting.caucho.BurlapProxyFactoryBean;
import org.springframework.remoting.caucho.HessianProxyFactoryBean;
//import org.springframework.remoting.httpinvoker.CommonsHttpInvokerRequestExecutor;
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import io.gh.dprimax.remoting.annotation.Remote;
import io.gh.dprimax.remoting.enumeration.Exposer;

public class ProxyBeanImporter implements BeanFactoryPostProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyBeanImporter.class);

	private static final String HTTP_PROTOCOL = "http://";
	private static final String RMI_PROTOCOL = "rmi://";

	private String basePackage;
	private String host;
	private String httpPort;
	private String httpContextPath = "";
	private String rmiPort;
	private String userName;
	private String password;

	public ProxyBeanImporter() {
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		PathMatchingResourcePatternResolver pathMatchingResolver = new PathMatchingResourcePatternResolver(
				beanFactory.getBeanClassLoader());
		try {
			Resource resources[] = pathMatchingResolver.getResources(resolvePathMatcher());
			Resource aresource[];
			int j = (aresource = resources).length;
			for (int i = 0; i < j; i++) {
				Resource resource = aresource[i];
				Class serviceInterface = getServiceInterface(resource, beanFactory.getBeanClassLoader());
				if (serviceInterface != null) {
					Remote annotationClazz = (Remote) AnnotationUtils.findAnnotation(serviceInterface, Remote.class);

					String beanId = getDefaultName(serviceInterface);
					if (!StringUtils.isEmpty(annotationClazz.name()))
						beanId = annotationClazz.name();

					BeanDefinition beanDefinition = createProxyBeanDefinition(beanId, serviceInterface,
							annotationClazz.exposer());

					DefaultListableBeanFactory listableFactory = (DefaultListableBeanFactory) beanFactory;
					listableFactory.registerBeanDefinition(beanId, beanDefinition);

					LOGGER.debug("Registering proxy class {} with id {}", serviceInterface, beanId);
				}
			}

		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	/**
	 * 
	 * @return
	 */
	private String resolvePathMatcher() {
		return (new StringBuilder("classpath*:")).append(ClassUtils.convertClassNameToResourcePath(getBasePackage()))
				.append("/**/*.class").toString();
	}

	@SuppressWarnings("rawtypes")
	private String getDefaultName(Class serviceInterfaceClass) {
		String shortClassName = ClassUtils.getShortName(serviceInterfaceClass);
		String firstLetter = shortClassName.substring(0, 1);
		return (new StringBuilder(String.valueOf(firstLetter.toLowerCase()))).append(shortClassName.substring(1))
				.toString();
	}

	@SuppressWarnings("rawtypes")
	private Class getServiceInterface(Resource resource, ClassLoader classLoader) {
		try {
			if (!resource.isReadable())
				return null;

			AnnotationTypeFilter filter = new AnnotationTypeFilter(Remote.class);
			SimpleMetadataReaderFactory simpleMetadataReaderFactory = new SimpleMetadataReaderFactory(classLoader);
			MetadataReader metadataReader = simpleMetadataReaderFactory.getMetadataReader(resource);

			if (filter.match(metadataReader, simpleMetadataReaderFactory)) {
				String className = metadataReader.getClassMetadata().getClassName();

				if (LOGGER.isDebugEnabled())
					LOGGER.debug("{} annotated with @Remote", className);

				return ClassUtils.forName(className, classLoader);
			}
		} catch (IOException e) {
			if (LOGGER.isErrorEnabled())
				LOGGER.error(e.toString());
		} catch (ClassNotFoundException e) {
			if (LOGGER.isErrorEnabled())
				LOGGER.error(e.toString());
		} catch (LinkageError e) {
			if (LOGGER.isErrorEnabled())
				LOGGER.error(e.toString());
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	private BeanDefinition createProxyBeanDefinition(String serviceName, Class serviceInterface, Exposer exposer) {

		// HTTP http://host:port/contextPath/serviceName
		String httpServiceUrl = new StringBuilder(HTTP_PROTOCOL).append(getHost()).append(":").append(getHttpPort())
				.append(getHttpContextPath()).append("/").append(serviceName).toString();

		// RMI rmi://host:port//serviceName
		String rmiServiceUrl = new StringBuilder(RMI_PROTOCOL).append(getHost()).append(":").append(getRmiPort())
				.append("/").append(serviceName).toString();

		BeanDefinitionBuilder beanDefinitionBuilder = null;

		// UsernamePasswordCredentials credentials = null;
		// if (!StringUtils.isEmpty(userName) && !StringUtils.isEmpty(password))
		// credentials = new UsernamePasswordCredentials(userName, password);

		if (Exposer.HESSIAN == exposer) {
			LOGGER.debug("Creating HESSIAN service definition");
			beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(HessianProxyFactoryBean.class)
					.addPropertyValue("serviceInterface", serviceInterface)
					.addPropertyValue("serviceUrl", httpServiceUrl);
			// if (null != credentials) {
			// beanDefinitionBuilder.addPropertyValue("username",
			// credentials.getUserName())
			// .addPropertyValue("password", credentials.getPassword());
			// }
		} else if (Exposer.HTTP == exposer) {
			LOGGER.debug("Creating HTTP service definition");
			beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(HttpInvokerProxyFactoryBean.class)
					.addPropertyValue("serviceInterface", serviceInterface)
					.addPropertyValue("serviceUrl", httpServiceUrl);
			// if (null != credentials) {
			// SimpleHttpState httpState = new SimpleHttpState();
			// httpState.setCredentials(credentials);
			// HttpClient httpClient = new HttpClient();
			// httpClient.setState(httpState);
			// beanDefinitionBuilder.addPropertyValue(
			// "httpInvokerRequestExecutor",
			// new CommonsHttpInvokerRequestExecutor(httpClient));
			// }
		} else if (Exposer.RMI == exposer) {
			LOGGER.debug("Creating RMI service definition");
			beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(RmiProxyFactoryBean.class)
					.addPropertyValue("serviceInterface", serviceInterface)
					.addPropertyValue("serviceUrl", rmiServiceUrl);
		}

		return beanDefinitionBuilder.getBeanDefinition();
	}

	public String getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(String httpPort) {
		this.httpPort = httpPort;
	}

	public String getHttpContextPath() {
		return httpContextPath;
	}

	public void setHttpContextPath(String httpContextPath) {
		this.httpContextPath = httpContextPath;
	}

	public String getRmiPort() {
		return rmiPort;
	}

	public void setRmiPort(String rmiPort) {
		this.rmiPort = rmiPort;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getBasePackage() {
		return basePackage;
	}

	public void setBasePackage(String basePackage) {
		this.basePackage = basePackage;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
