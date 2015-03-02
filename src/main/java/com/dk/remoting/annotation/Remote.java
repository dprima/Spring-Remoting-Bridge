package com.dk.remoting.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

import com.dk.remoting.enumeration.Exposer;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Remote {

	public abstract String name() default "";

	public abstract Exposer exposer() default Exposer.HTTP;
}
