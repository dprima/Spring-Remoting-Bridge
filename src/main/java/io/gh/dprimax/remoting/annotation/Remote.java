package io.gh.dprimax.remoting.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.gh.dprimax.remoting.enumeration.Exposer;

/**
 * 
 * @author dprimax
 *
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Remote {

	public abstract String name() default "";

	public abstract Exposer exposer() default Exposer.HTTP;
}
