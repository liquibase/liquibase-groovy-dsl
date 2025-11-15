package org.liquibase.groovy.delegate

import java.lang.annotation.Retention
import java.lang.annotation.Target
import static java.lang.annotation.ElementType.*
import static java.lang.annotation.RetentionPolicy.*

@Target([PARAMETER])
@Retention(RUNTIME)
@interface Since {
	String value() default ''
	String oldName() default ''
}
