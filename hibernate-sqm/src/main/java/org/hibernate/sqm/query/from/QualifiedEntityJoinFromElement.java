/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or visit http://www.apache.org/licenses/LICENSE-2.0
 */
package org.hibernate.sqm.query.from;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import org.hibernate.sqm.SemanticQueryWalker;
import org.hibernate.sqm.query.JoinType;
import org.hibernate.sqm.query.predicate.Predicate;

/**
 * @author Steve Ebersole
 */
public class QualifiedEntityJoinFromElement
		extends AbstractJoinedFromElement
		implements QualifiedJoinedFromElement {
	private final String entityName;

	private Predicate onClausePredicate;

	public QualifiedEntityJoinFromElement(
			FromElementSpace fromElementSpace,
			String alias,
			EntityType joinedEntityDescriptor,
			JoinType joinType) {
		super( fromElementSpace, alias, joinedEntityDescriptor, joinType );
		this.entityName = joinedEntityDescriptor.getName();
	}

	public String getEntityName() {
		return entityName;
	}

	@Override
	public EntityType getBindableModelDescriptor() {
		return (EntityType) super.getBindableModelDescriptor();
	}

	@Override
	public Attribute resolveAttribute(String attributeName) {
		return getBindableModelDescriptor().getAttribute( attributeName );
	}

	@Override
	public Predicate getOnClausePredicate() {
		return onClausePredicate;
	}

	public void setOnClausePredicate(Predicate predicate) {
		this.onClausePredicate = predicate;
	}

	@Override
	public <T> T accept(SemanticQueryWalker<T> walker) {
		return walker.visitQualifiedEntityJoinFromElement( this );
	}
}
