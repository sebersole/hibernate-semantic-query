/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or visit http://www.apache.org/licenses/LICENSE-2.0
 */
package org.hibernate.sqm.query.expression;

import javax.persistence.metamodel.Type;

import org.hibernate.sqm.SemanticQueryWalker;
import org.hibernate.sqm.query.from.FromElement;

/**
 * @author Steve Ebersole
 */
public class MapKeyFunction implements Expression {
	private final String collectionAlias;
	private final Type indexType;

	public MapKeyFunction(FromElement collectionReference, Type indexType) {
		this.collectionAlias = collectionReference.getAlias();
		this.indexType = indexType;
	}

	public String getCollectionAlias() {
		return collectionAlias;
	}

	public Type getMapKeyType() {
		return indexType;
	}

	@Override
	public Type getTypeDescriptor() {
		return getMapKeyType();
	}

	@Override
	public Type getInferableType() {
		return getMapKeyType();
	}

	@Override
	public <T> T accept(SemanticQueryWalker<T> walker) {
		return walker.visitMapKeyFunction( this );
	}
}
