package io.undertow.jsp;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

import org.apache.tomcat.InstanceManager;

/**
 *
 * InstanceManager is evil and needs to go away
 *
 * @author Stuart Douglas
 */
public class HackInstanceManager implements InstanceManager {
    @Override
    public Object newInstance(final String className) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException {
        return newInstance(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
    }

    @Override
    public Object newInstance(final String fqcn, final ClassLoader classLoader) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException {
        return classLoader.loadClass(fqcn).newInstance();
    }

    @Override
    public Object newInstance(final Class<?> c) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException {
        return c.newInstance();
    }

    @Override
    public void newInstance(final Object o) throws IllegalAccessException, InvocationTargetException, NamingException {

    }

    @Override
    public void destroyInstance(final Object o) throws IllegalAccessException, InvocationTargetException {

    }
}
