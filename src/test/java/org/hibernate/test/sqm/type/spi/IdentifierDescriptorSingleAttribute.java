/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.test.sqm.type.spi;

/**
 * Base information describing an identifier which can be referenced through a single attribute
 *
 * @author Steve Ebersole
 */
public interface IdentifierDescriptorSingleAttribute extends IdentifierDescriptor {
	SingularAttribute getIdAttribute();
}
