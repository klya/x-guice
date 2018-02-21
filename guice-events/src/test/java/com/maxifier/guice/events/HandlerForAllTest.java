package com.maxifier.guice.events;


import org.testng.annotations.Test;

import java.util.LinkedList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/*
* Project: Maxifier
* Author: Aleksey Didik
* Created: 23.05.2008 10:19:35
* 
* Copyright (c) 1999-2009 Magenta Corporation Ltd. All Rights Reserved.
* Magenta Technology proprietary and confidential.
* Use is subject to license terms.
*/
public class HandlerForAllTest {

    @Test
    public void testHandleAll() {
        AllWrapper wrapper = new AllWrapper();
        All spy = wrapper.spy;
        EventDispatcher ed = new EventDispatcherImpl(new ListenerRegistrationQueue());
        ed.register(wrapper);
        ed.fireEvent(Animal.CROCODILE);
        ed.fireEvent("");
        ed.fireEvent(4);
        LinkedList linkedList = new LinkedList();
        ed.fireEvent(linkedList);
        verify(spy).hurrai(Animal.CROCODILE);
        verify(spy).hurrai("");
        verify(spy).hurrai(4);
        verify(spy).hurrai(linkedList);
    }

    interface All {
        void hurrai(Object object);
    }

    static class AllWrapper implements All {
        final All spy = mock(All.class);

        @Handler
        public void hurrai(Object object) {
            spy.hurrai(object);
        }
    }
}
