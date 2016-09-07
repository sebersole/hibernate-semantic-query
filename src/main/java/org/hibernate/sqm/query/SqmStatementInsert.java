/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or visit http://www.apache.org/licenses/LICENSE-2.0
 */
package org.hibernate.sqm.query;

import java.util.List;

import org.hibernate.sqm.query.expression.AttributeReferenceSqmExpression;
import org.hibernate.sqm.query.from.RootEntityFromElement;

/**
 * @author Steve Ebersole
 */
public interface SqmStatementInsert extends SqmStatement {
	RootEntityFromElement getInsertTarget();
	List<AttributeReferenceSqmExpression> getStateFields();
}