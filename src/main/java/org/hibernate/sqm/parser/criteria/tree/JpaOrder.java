/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sqm.parser.criteria.tree;

import javax.persistence.criteria.Order;

/**
 * @author Steve Ebersole
 */
public interface JpaOrder extends Order {
	@Override
	JpaExpression<?> getExpression();
}
