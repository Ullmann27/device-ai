package androidx.annotation;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
public @interface RequiresApi { int value() default 1; int api() default 1; }
