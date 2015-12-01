/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or visit http://www.apache.org/licenses/LICENSE-2.0
 */
package org.hibernate.test.sqm.domain;

import javax.persistence.metamodel.BasicType;

/**
 * @author Steve Ebersole
 */
public class BasicTypeImpl implements BasicType {
	private final Class javaType;

	public BasicTypeImpl(Class javaType) {
		this.javaType = javaType;
	}

	@Override
	public PersistenceType getPersistenceType() {
		return PersistenceType.BASIC;
	}

	@Override
	public Class getJavaType() {
		return javaType;
	}
}
