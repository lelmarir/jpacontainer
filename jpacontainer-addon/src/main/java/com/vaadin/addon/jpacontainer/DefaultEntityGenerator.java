package com.vaadin.addon.jpacontainer;

public class DefaultEntityGenerator<E> implements EntityGenerator<E> {

	private Class<E> type;

	public DefaultEntityGenerator(Class<E> type) {
		this.type = type;
	}
	
	@Override
	public E createEntity() {
		try {
			return type.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

}
