package com.vaadin.addon.jpacontainer;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class JPAContainerItemPluralProperty<T, P> extends JPAContainerItemProperty<T, P> {

	private static final long serialVersionUID = -822611726271180424L;

	private class SetChangeInteceptor implements MethodInterceptor {
		private Object value;

		public SetChangeInteceptor(Object value) {
			this.value = value;
		}

		@Override
		public Object intercept(Object target, Method method, Object[] args, MethodProxy proxy) throws Throwable {
			Object targetReturn = proxy.invoke(value, args);
			if (hasModifiedTheCollection(method)) {
				if (getItem().isWriteThrough()) {
					getItem().containerItemPropertyModified(getPropertyId());
				} else {
					setModified(true);
				}
				fireValueChangeEvent();
			}
			return targetReturn;
		}
	}

	private static boolean hasModifiedTheCollection(Method method) {
		String mName = method.getName();
		if (mName.startsWith("add") || mName.equals("clear") || mName.startsWith("remove") || mName.startsWith("retain")
				|| mName.equals("set") || mName.startsWith("offer") || mName.startsWith("poll") || mName.equals("pop")
				|| mName.equals("push") || mName.equals("put")) {
			return true;
		} else {
			return false;
		}
	}

	JPAContainerItemPluralProperty(JPAContainerItem<T> item, String propertyId) {
		super(item, propertyId);
	}

	@Override
	public P getValue() {
		Enhancer e = new Enhancer();
		e.setSuperclass(getType());
		e.setCallback(new SetChangeInteceptor(super.getValue()));
		return (P) e.create();
	}
}
