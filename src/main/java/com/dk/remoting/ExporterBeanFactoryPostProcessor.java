package com.dk.remoting;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.remoting.caucho.BurlapServiceExporter;
import org.springframework.remoting.caucho.HessianServiceExporter;
import org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter;
import org.springframework.remoting.rmi.RmiServiceExporter;
import org.springframework.util.ClassUtils;

import com.dk.remoting.annotation.Remote;
import com.dk.remoting.enumeration.Exposer;

public class ExporterBeanFactoryPostProcessor implements
		BeanFactoryPostProcessor {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(ExporterBeanFactoryPostProcessor.class);

	private String rmiRegistryHost;
	private int rmiRegistryPort;
	private boolean alwaysCreateRegistry = true;

	public String getRmiRegistryHost() {
		return rmiRegistryHost;
	}

	public void setRmiRegistryHost(String rmiRegistryHost) {
		this.rmiRegistryHost = rmiRegistryHost;
	}

	public int getRmiRegistryPort() {
		return rmiRegistryPort;
	}

	public void setRmiRegistryPort(int rmiRegistryPort) {
		this.rmiRegistryPort = rmiRegistryPort;
	}

	public boolean isAlwaysCreateRegistry() {
		return alwaysCreateRegistry;
	}

	public void setAlwaysCreateRegistry(boolean alwaysCreateRegistry) {
		this.alwaysCreateRegistry = alwaysCreateRegistry;
	}

	public ExporterBeanFactoryPostProcessor() {
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException {
		BeanFactory bf = beanFactory;
		do {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Scan from factory: {}", bf);

			String beanDefinitionNames[] = ((ListableBeanFactory) bf)
					.getBeanDefinitionNames();
			String as[];
			int j = (as = beanDefinitionNames).length;
			for (int i = 0; i < j; i++) {
				String name = as[i];
				BeanDefinition beanDefinition = ((ConfigurableListableBeanFactory) bf)
						.getBeanDefinition(name);
				String clazz = beanDefinition.getBeanClassName();

				if (LOGGER.isDebugEnabled())
					LOGGER.debug("###########Found class: {} ", clazz);

				if (clazz != null) {
					Class serviceInterface = getServiceInterface(clazz,
							beanFactory.getBeanClassLoader());
					if (serviceInterface != null) {
						Remote annotationClazz = (Remote) AnnotationUtils
								.findAnnotation(serviceInterface, Remote.class);
						String remotingName = (new StringBuilder("/")).append(
								getDefaultName(serviceInterface)).toString();
						if (!StringUtils.isEmpty(annotationClazz.name()))
							remotingName = (new StringBuilder("/")).append(
									annotationClazz.name()).toString();

						if (LOGGER.isDebugEnabled())
							LOGGER.debug(
									"###########Registering service class {} with name {}",
									clazz, remotingName);

						BeanDefinition bd = createExporterBeanDefinition(
								remotingName, name, serviceInterface,
								annotationClazz.exposer());
						((DefaultListableBeanFactory) beanFactory)
								.registerBeanDefinition(remotingName, bd);
					}
				}
			}

		} while ((bf = ((HierarchicalBeanFactory) bf).getParentBeanFactory()) != null);

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("#########################################postProcessBeanFactory SUCCESS...");

	}

	/**
	 * 
	 * @param serviceInterfaceClass
	 * @return class name of interface with lowered first letter if no name
	 *         specified
	 */
	@SuppressWarnings("rawtypes")
	private String getDefaultName(Class serviceInterfaceClass) {
		String shortClassName = ClassUtils.getShortName(serviceInterfaceClass);
		String firstLetter = shortClassName.substring(0, 1);
		return (new StringBuilder(String.valueOf(firstLetter.toLowerCase())))
				.append(shortClassName.substring(1)).toString();
	}

	/**
	 * 
	 * @param beanName
	 * @param serviceName
	 * @param serviceInterface
	 * @param exposer
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private BeanDefinition createExporterBeanDefinition(String beanName,
			String serviceName, Class serviceInterface, Exposer exposer) {

		BeanDefinitionBuilder beanDefinitionBuilder = null;

		if (Exposer.BURLAP == exposer) {
			beanDefinitionBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(BurlapServiceExporter.class)
					.addPropertyReference("service", serviceName)
					.addPropertyValue("serviceInterface", serviceInterface);
		} else if (Exposer.HESSIAN == exposer) {
			beanDefinitionBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(HessianServiceExporter.class)
					.addPropertyReference("service", serviceName)
					.addPropertyValue("serviceInterface", serviceInterface);
		} else if (Exposer.HTTP == exposer) {
			beanDefinitionBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(HttpInvokerServiceExporter.class)
					.addPropertyReference("service", serviceName)
					.addPropertyValue("serviceInterface", serviceInterface);
		} else if (Exposer.RMI == exposer) {
			beanDefinitionBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(RmiServiceExporter.class)
					.addPropertyReference("service", serviceName)
					.addPropertyValue("serviceName",
							beanName.substring(1, beanName.length()))
					.addPropertyValue("serviceInterface", serviceInterface)
					.addPropertyValue("alwaysCreateRegistry",
							isAlwaysCreateRegistry());
			if (0 != getRmiRegistryPort())
				beanDefinitionBuilder.addPropertyValue("registryPort",
						getRmiRegistryPort());
			if (!StringUtils.isEmpty(getRmiRegistryHost()))
				beanDefinitionBuilder.addPropertyValue("registryHost",
						getRmiRegistryHost());

			if (LOGGER.isDebugEnabled())
				LOGGER.debug(
						"Building RmiServiceExporter with [service={}, serviceName={}, serviceInterface={}, registryHost={}, registryPort={}]",
						new Object[] {
								serviceName,
								beanName,
								serviceInterface,
								beanDefinitionBuilder.getBeanDefinition()
										.getPropertyValues()
										.getPropertyValue("registryHost"),
								beanDefinitionBuilder.getBeanDefinition()
										.getPropertyValues()
										.getPropertyValue("registryPort") });
		}

		return beanDefinitionBuilder.getBeanDefinition();
	}

	/**
	 * 
	 * @param className
	 * @param classLoader
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private Class getServiceInterface(String className, ClassLoader classLoader) {
		try {
			if (StringUtils.isEmpty(className))
				return null;

			AnnotationTypeFilter filter = new AnnotationTypeFilter(Remote.class);
			SimpleMetadataReaderFactory simpleMetadataReaderFactory = new SimpleMetadataReaderFactory(
					classLoader);
			MetadataReader metadataReader = simpleMetadataReaderFactory
					.getMetadataReader(className);
			String interfaceNames[] = metadataReader.getClassMetadata()
					.getInterfaceNames();

			String as[];
			int i = 0;
			int j = (as = interfaceNames).length;
			String ifName = "";

			if (as.length > 0)
				ifName = as[i];

			if (!StringUtils.isEmpty(ifName))
				if (i < j) {
					MetadataReader ifMetaDataReader = simpleMetadataReaderFactory
							.getMetadataReader(ifName);
					if (filter.match(ifMetaDataReader,
							simpleMetadataReaderFactory)) {
						String clazzName = metadataReader.getClassMetadata()
								.getClassName();

						if (LOGGER.isDebugEnabled())
							LOGGER.debug(
									"Class {} implements interface {} which annotated with @Remote",
									clazzName, ifName);

						return ClassUtils.forName(ifName, classLoader);
					}
					i++;
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
}
