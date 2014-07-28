package com.magenta.guice.lifecycle;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.ProvisionException;
import org.junit.Test;

import javax.annotation.PostConstruct;

/**
 * @author aleksey.didik@maxifier.com (Aleksey Didik)
 */
public class PostConstructExceptionTest {

    @Test(expected = CreationException.class)
    public void testExceptionInPostConstruct() throws Exception {
        Guice.createInjector(new LifecycleModule(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(Foo.class).asEagerSingleton();
            }
        });
    }

    static class Foo {

        @PostConstruct
        void initWithexception() {
            throw new RuntimeException("Bad news");
        }
    }
}
