/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: Apache License, Version 2.0
 * See the LICENSE file in the root directory or visit http://www.apache.org/licenses/LICENSE-2.0
 */
package org.hibernate.sqm.parser.hql.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.sqm.domain.BasicType;
import org.hibernate.sqm.domain.EntityType;
import org.hibernate.sqm.domain.PluralAttribute;
import org.hibernate.sqm.domain.PolymorphicEntityType;
import org.hibernate.sqm.domain.Type;
import org.hibernate.sqm.parser.LiteralNumberFormatException;
import org.hibernate.sqm.parser.ParsingException;
import org.hibernate.sqm.parser.SemanticException;
import org.hibernate.sqm.StrictJpaComplianceViolation;
import org.hibernate.sqm.parser.common.FromElementLocator;
import org.hibernate.sqm.parser.common.QuerySpecProcessingState;
import org.hibernate.sqm.parser.common.QuerySpecProcessingStateDmlImpl;
import org.hibernate.sqm.parser.common.QuerySpecProcessingStateStandardImpl;
import org.hibernate.sqm.parser.hql.internal.path.PathHelper;
import org.hibernate.sqm.parser.hql.internal.path.PathResolverBasicImpl;
import org.hibernate.sqm.parser.hql.internal.path.PathResolverJoinAttributeImpl;
import org.hibernate.sqm.parser.hql.internal.path.PathResolverJoinPredicateImpl;
import org.hibernate.sqm.parser.hql.internal.path.PathResolverStack;
import org.hibernate.sqm.parser.common.ResolutionContext;
import org.hibernate.sqm.parser.common.ExpressionTypeHelper;
import org.hibernate.sqm.parser.common.FromElementBuilder;
import org.hibernate.sqm.parser.common.ImplicitAliasGenerator;
import org.hibernate.sqm.parser.common.ParsingContext;
import org.hibernate.sqm.parser.hql.internal.antlr.HqlParser;
import org.hibernate.sqm.parser.hql.internal.antlr.HqlParserBaseVisitor;
import org.hibernate.sqm.path.AttributeBinding;
import org.hibernate.sqm.path.AttributeBindingSource;
import org.hibernate.sqm.path.Binding;
import org.hibernate.sqm.query.DeleteStatement;
import org.hibernate.sqm.query.InsertSelectStatement;
import org.hibernate.sqm.query.JoinType;
import org.hibernate.sqm.query.QuerySpec;
import org.hibernate.sqm.query.SelectStatement;
import org.hibernate.sqm.query.Statement;
import org.hibernate.sqm.query.UpdateStatement;
import org.hibernate.sqm.query.expression.function.AggregateSqmFunction;
import org.hibernate.sqm.query.expression.AttributeReferenceSqmExpression;
import org.hibernate.sqm.query.expression.function.AvgSqmFunction;
import org.hibernate.sqm.query.expression.BinaryArithmeticSqmExpression;
import org.hibernate.sqm.query.expression.CaseSearchedSqmExpression;
import org.hibernate.sqm.query.expression.CoalesceSqmExpression;
import org.hibernate.sqm.query.expression.CollectionIndexSqmFunction;
import org.hibernate.sqm.query.expression.CollectionSizeSqmFunction;
import org.hibernate.sqm.query.expression.CollectionValuePathSqmExpression;
import org.hibernate.sqm.query.expression.ConcatSqmExpression;
import org.hibernate.sqm.query.expression.ConstantEnumSqmExpression;
import org.hibernate.sqm.query.expression.ConstantSqmExpression;
import org.hibernate.sqm.query.expression.ConstantFieldSqmExpression;
import org.hibernate.sqm.query.expression.function.CastFunctionSqmExpression;
import org.hibernate.sqm.query.expression.function.ConcatFunctionSqmExpression;
import org.hibernate.sqm.query.expression.function.CountSqmFunction;
import org.hibernate.sqm.query.expression.function.CountStarSqmFunction;
import org.hibernate.sqm.query.expression.EntityTypeSqmExpression;
import org.hibernate.sqm.query.expression.SqmExpression;
import org.hibernate.sqm.query.expression.function.GenericFunctionSqmExpression;
import org.hibernate.sqm.query.expression.ImpliedTypeSqmExpression;
import org.hibernate.sqm.query.expression.LiteralBigDecimalSqmExpression;
import org.hibernate.sqm.query.expression.LiteralBigIntegerSqmExpression;
import org.hibernate.sqm.query.expression.LiteralCharacterSqmExpression;
import org.hibernate.sqm.query.expression.LiteralDoubleSqmExpression;
import org.hibernate.sqm.query.expression.LiteralSqmExpression;
import org.hibernate.sqm.query.expression.LiteralFalseSqmExpression;
import org.hibernate.sqm.query.expression.LiteralFloatSqmExpression;
import org.hibernate.sqm.query.expression.LiteralIntegerSqmExpression;
import org.hibernate.sqm.query.expression.LiteralLongSqmExpression;
import org.hibernate.sqm.query.expression.LiteralNullSqmExpression;
import org.hibernate.sqm.query.expression.LiteralStringSqmExpression;
import org.hibernate.sqm.query.expression.LiteralTrueSqmExpression;
import org.hibernate.sqm.query.expression.MapEntrySqmFunction;
import org.hibernate.sqm.query.expression.MapKeyPathSqmExpression;
import org.hibernate.sqm.query.expression.MaxElementSqmFunction;
import org.hibernate.sqm.query.expression.function.LowerFunctionSqmExpression;
import org.hibernate.sqm.query.expression.function.MaxSqmFunction;
import org.hibernate.sqm.query.expression.MaxIndexSqmFunction;
import org.hibernate.sqm.query.expression.MinElementSqmFunction;
import org.hibernate.sqm.query.expression.function.MinSqmFunction;
import org.hibernate.sqm.query.expression.MinIndexSqmFunction;
import org.hibernate.sqm.query.expression.NamedParameterSqmExpression;
import org.hibernate.sqm.query.expression.NullifSqmExpression;
import org.hibernate.sqm.query.expression.PluralAttributeIndexedReference;
import org.hibernate.sqm.query.expression.PositionalParameterSqmExpression;
import org.hibernate.sqm.query.expression.CaseSimpleSqmExpression;
import org.hibernate.sqm.query.expression.SubQuerySqmExpression;
import org.hibernate.sqm.query.expression.function.SubstringFunctionSqmExpression;
import org.hibernate.sqm.query.expression.function.SumSqmFunction;
import org.hibernate.sqm.query.expression.UnaryOperationSqmExpression;
import org.hibernate.sqm.query.expression.function.TrimFunctionSqmExpression;
import org.hibernate.sqm.query.expression.function.UpperFunctionSqmExpression;
import org.hibernate.sqm.query.from.CrossJoinedFromElement;
import org.hibernate.sqm.query.from.FromClause;
import org.hibernate.sqm.query.from.FromElement;
import org.hibernate.sqm.query.from.FromElementSpace;
import org.hibernate.sqm.query.from.JoinedFromElement;
import org.hibernate.sqm.query.from.QualifiedAttributeJoinFromElement;
import org.hibernate.sqm.query.from.QualifiedJoinedFromElement;
import org.hibernate.sqm.query.from.RootEntityFromElement;
import org.hibernate.sqm.query.order.OrderByClause;
import org.hibernate.sqm.query.order.SortOrder;
import org.hibernate.sqm.query.order.SortSpecification;
import org.hibernate.sqm.query.predicate.AndSqmPredicate;
import org.hibernate.sqm.query.predicate.BetweenSqmPredicate;
import org.hibernate.sqm.query.predicate.EmptinessSqmPredicate;
import org.hibernate.sqm.query.predicate.GroupedSqmPredicate;
import org.hibernate.sqm.query.predicate.InSubQuerySqmPredicate;
import org.hibernate.sqm.query.predicate.InListSqmPredicate;
import org.hibernate.sqm.query.predicate.LikeSqmPredicate;
import org.hibernate.sqm.query.predicate.MemberOfSqmPredicate;
import org.hibernate.sqm.query.predicate.NegatableSqmPredicate;
import org.hibernate.sqm.query.predicate.NegatedSqmPredicate;
import org.hibernate.sqm.query.predicate.NullnessSqmPredicate;
import org.hibernate.sqm.query.predicate.OrSqmPredicate;
import org.hibernate.sqm.query.predicate.SqmPredicate;
import org.hibernate.sqm.query.predicate.RelationalSqmPredicate;
import org.hibernate.sqm.query.predicate.WhereClause;
import org.hibernate.sqm.query.select.DynamicInstantiation;
import org.hibernate.sqm.query.select.DynamicInstantiationArgument;
import org.hibernate.sqm.query.select.SelectClause;
import org.hibernate.sqm.query.select.Selection;

import org.jboss.logging.Logger;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * @author Steve Ebersole
 */
public class SemanticQueryBuilder extends HqlParserBaseVisitor {
	private static final Logger log = Logger.getLogger( SemanticQueryBuilder.class );

	/**
	 * Main entry point into analysis of HQL/JPQL parse tree - producing a semantic model of the
	 * sqm.
	 *
	 * @param statement The statement to analyze.
	 *
	 * @param parsingContext Access to things needed to perform the analysis
	 * @return The semantic sqm model
	 */
	public static Statement buildSemanticModel(HqlParser.StatementContext statement, ParsingContext parsingContext) {
		return new SemanticQueryBuilder( parsingContext ).visitStatement( statement );
	}

	private final ParsingContext parsingContext;

	private boolean inWhereClause;
	private final PathResolverStack pathResolverStack = new PathResolverStack();
	private QuerySpecProcessingState currentQuerySpecProcessingState;


	private  SemanticQueryBuilder(ParsingContext parsingContext) {
		this.parsingContext = parsingContext;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Grammar rules

	@Override
	public Statement visitStatement(HqlParser.StatementContext ctx) {
		if ( ctx.insertStatement() != null ) {
			return visitInsertStatement( ctx.insertStatement() );
		}
		else if ( ctx.updateStatement() != null ) {
			return visitUpdateStatement( ctx.updateStatement() );
		}
		else if ( ctx.deleteStatement() != null ) {
			return visitDeleteStatement( ctx.deleteStatement() );
		}
		else if ( ctx.selectStatement() != null ) {
			return visitSelectStatement( ctx.selectStatement() );
		}

		throw new ParsingException( "Unexpected statement type [not INSERT, UPDATE, DELETE or SELECT] : " + ctx.getText() );
	}

	@Override
	public SelectStatement visitSelectStatement(HqlParser.SelectStatementContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			if ( ctx.querySpec().selectClause() == null ) {
				throw new StrictJpaComplianceViolation(
						"Encountered implicit select-clause, but strict JPQL compliance was requested",
						StrictJpaComplianceViolation.Type.IMPLICIT_SELECT
				);
			}
		}

		final SelectStatement selectStatement = new SelectStatement();
		selectStatement.applyQuerySpec( visitQuerySpec( ctx.querySpec() ) );
		if ( ctx.orderByClause() != null ) {
			pathResolverStack.push(
					new PathResolverBasicImpl( new OrderByResolutionContext( parsingContext, selectStatement ) )
			);
			try {
				selectStatement.applyOrderByClause( visitOrderByClause( ctx.orderByClause() ) );
			}
			finally {
				pathResolverStack.pop();
			}
		}

		return selectStatement;
	}

	@Override
	public QuerySpec visitQuerySpec(HqlParser.QuerySpecContext ctx) {
		currentQuerySpecProcessingState = new QuerySpecProcessingStateStandardImpl( parsingContext, currentQuerySpecProcessingState );
		pathResolverStack.push( new PathResolverBasicImpl( currentQuerySpecProcessingState ) );
		try {
			// visit from-clause first!!!
			visitFromClause( ctx.fromClause() );

			final SelectClause selectClause;
			if ( ctx.selectClause() != null ) {
				selectClause = visitSelectClause( ctx.selectClause() );
			}
			else {
				log.info( "Encountered implicit select clause which is a deprecated feature : " + ctx.getText() );
				selectClause = buildInferredSelectClause( currentQuerySpecProcessingState.getFromClause() );
			}

			final WhereClause whereClause;
			if ( ctx.whereClause() != null ) {
				whereClause = visitWhereClause( ctx.whereClause() );
			}
			else {
				whereClause = null;
			}
			return new QuerySpec( currentQuerySpecProcessingState.getFromClause(), selectClause, whereClause );
		}
		finally {
			pathResolverStack.pop();
			currentQuerySpecProcessingState = currentQuerySpecProcessingState.getParent();
		}
	}

	protected SelectClause buildInferredSelectClause(FromClause fromClause) {
		// for now, this is slightly different than the legacy behavior where
		// the root and each non-fetched-join was selected.  For now, here, we simply
		// select the root
		final SelectClause selectClause = new SelectClause( true );
		final FromElement root = fromClause.getFromElementSpaces().get( 0 ).getRoot();
		selectClause.addSelection( new Selection( root ) );
		return selectClause;
	}

	@Override
	public SelectClause visitSelectClause(HqlParser.SelectClauseContext ctx) {
		final SelectClause selectClause = new SelectClause( ctx.DISTINCT() != null );
		for ( HqlParser.SelectionContext selectionContext : ctx.selectionList().selection() ) {
			selectClause.addSelection( visitSelection( selectionContext ) );
		}
		return selectClause;
	}

	@Override
	public Selection visitSelection(HqlParser.SelectionContext ctx) {
		final Selection selection = new Selection(
				visitSelectExpression( ctx.selectExpression() ),
				interpretResultIdentifier( ctx.resultIdentifier() )
		);
		currentQuerySpecProcessingState.getFromElementBuilder().getAliasRegistry().registerAlias( selection );
		return selection;
	}

	private String interpretResultIdentifier(HqlParser.ResultIdentifierContext resultIdentifierContext) {
		if ( resultIdentifierContext != null ) {
			final String explicitAlias;
			if ( resultIdentifierContext.AS() != null ) {
				final Token aliasToken = resultIdentifierContext.identifier().getStart();
				explicitAlias = aliasToken.getText();

				if ( aliasToken.getType() != HqlParser.IDENTIFIER ) {
					// we have a reserved word used as an identification variable.
					if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
						throw new StrictJpaComplianceViolation(
								String.format(
										Locale.ROOT,
										"Strict JPQL compliance was violated : %s [%s]",
										StrictJpaComplianceViolation.Type.RESERVED_WORD_USED_AS_ALIAS.description(),
										explicitAlias
								),
								StrictJpaComplianceViolation.Type.RESERVED_WORD_USED_AS_ALIAS
						);
					}
				}
			}
			else {
				explicitAlias = resultIdentifierContext.getText();
			}
			return explicitAlias;
		}

		return parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
	}

	private String interpretAlias(HqlParser.IdentifierContext identifier) {
		if ( identifier == null || identifier.getText() == null ) {
			return parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
		}
		return identifier.getText();
	}

	private String interpretAlias(TerminalNode aliasNode) {
		if ( aliasNode == null ) {
			return parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
		}

		// todo : not sure I like asserts for this kind of thing.  They are generally disable in runtime environments.
		// either the thing is important to check or it isn't.
		assert aliasNode.getSymbol().getType() == HqlParser.IDENTIFIER;

		return aliasNode.getText();
	}

	@Override
	public SqmExpression visitSelectExpression(HqlParser.SelectExpressionContext ctx) {
		if ( ctx.dynamicInstantiation() != null ) {
			return visitDynamicInstantiation( ctx.dynamicInstantiation() );
		}
		else if ( ctx.jpaSelectObjectSyntax() != null ) {
			return visitJpaSelectObjectSyntax( ctx.jpaSelectObjectSyntax() );
		}
		else if ( ctx.expression() != null ) {
			return (SqmExpression) ctx.expression().accept( this );
		}

		throw new ParsingException( "Unexpected selection rule type : " + ctx.getText() );
	}

	@Override
	public DynamicInstantiation visitDynamicInstantiation(HqlParser.DynamicInstantiationContext ctx) {
		final DynamicInstantiation dynamicInstantiation;

		if ( ctx.dynamicInstantiationTarget().MAP() != null ) {
			final BasicType<Map> mapType = parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Map.class );
			dynamicInstantiation = DynamicInstantiation.forMapInstantiation();
		}
		else if ( ctx.dynamicInstantiationTarget().LIST() != null ) {
			final BasicType<List> listType = parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( List.class );
			dynamicInstantiation = DynamicInstantiation.forListInstantiation();
		}
		else {
			final String className = ctx.dynamicInstantiationTarget().dotIdentifierSequence().getText();
			try {
				final Class targetJavaType = parsingContext.getConsumerContext().classByName( className );
				dynamicInstantiation = DynamicInstantiation.forClassInstantiation( targetJavaType );
			}
			catch (ClassNotFoundException e) {
				throw new SemanticException( "Unable to resolve class named for dynamic instantiation : " + className );
			}
		}

		for ( HqlParser.DynamicInstantiationArgContext arg : ctx.dynamicInstantiationArgs().dynamicInstantiationArg() ) {
			dynamicInstantiation.addArgument( visitDynamicInstantiationArg( arg ) );
		}

		return dynamicInstantiation;
	}

	@Override
	public DynamicInstantiationArgument visitDynamicInstantiationArg(HqlParser.DynamicInstantiationArgContext ctx) {
		return new DynamicInstantiationArgument(
				visitDynamicInstantiationArgExpression( ctx.dynamicInstantiationArgExpression() ),
				ctx.identifier() == null ? null : ctx.identifier().getText()
		);
	}

	@Override
	public SqmExpression visitDynamicInstantiationArgExpression(HqlParser.DynamicInstantiationArgExpressionContext ctx) {
		if ( ctx.dynamicInstantiation() != null ) {
			return visitDynamicInstantiation( ctx.dynamicInstantiation() );
		}
		else if ( ctx.expression() != null ) {
			return (SqmExpression) ctx.expression().accept( this );
		}

		throw new ParsingException( "Unexpected dynamic-instantiation-argument rule type : " + ctx.getText() );
	}

	@Override
	public FromElement visitJpaSelectObjectSyntax(HqlParser.JpaSelectObjectSyntaxContext ctx) {
		final String alias = ctx.identifier().getText();
		final FromElement fromElement = currentQuerySpecProcessingState.getFromElementBuilder().getAliasRegistry().findFromElementByAlias( alias );
		if ( fromElement == null ) {
			throw new SemanticException( "Unable to resolve alias [" +  alias + "] in selection [" + ctx.getText() + "]" );
		}
		return fromElement;
	}

	@Override
	public WhereClause visitWhereClause(HqlParser.WhereClauseContext ctx) {
		inWhereClause = true;

		try {
			return new WhereClause( (SqmPredicate) ctx.predicate().accept( this ) );
		}
		finally {
			inWhereClause = false;
		}
	}

	@Override
	public Object visitGroupByClause(HqlParser.GroupByClauseContext ctx) {
		return super.visitGroupByClause( ctx );
	}

	@Override
	public Object visitHavingClause(HqlParser.HavingClauseContext ctx) {
		return super.visitHavingClause( ctx );
	}

	@Override
	public GroupedSqmPredicate visitGroupedPredicate(HqlParser.GroupedPredicateContext ctx) {
		return new GroupedSqmPredicate( (SqmPredicate) ctx.predicate().accept( this ) );
	}

	private static class OrderByResolutionContext implements ResolutionContext, FromElementLocator {
		private final ParsingContext parsingContext;
		private final SelectStatement selectStatement;

		public OrderByResolutionContext(ParsingContext parsingContext, SelectStatement selectStatement) {
			this.parsingContext = parsingContext;
			this.selectStatement = selectStatement;
		}

		@Override
		public FromElement findFromElementByIdentificationVariable(String identificationVariable) {
			for ( FromElementSpace fromElementSpace : selectStatement.getQuerySpec().getFromClause().getFromElementSpaces() ) {
				if ( fromElementSpace.getRoot().getIdentificationVariable().equals( identificationVariable ) ) {
					return fromElementSpace.getRoot();
				}

				for ( JoinedFromElement joinedFromElement : fromElementSpace.getJoins() ) {
					if ( joinedFromElement.getIdentificationVariable().equals( identificationVariable ) ) {
						return joinedFromElement;
					}
				}
			}

			// otherwise there is none
			return null;
		}

		@Override
		public FromElement findFromElementExposingAttribute(String attributeName) {
			for ( FromElementSpace fromElementSpace : selectStatement.getQuerySpec().getFromClause().getFromElementSpaces() ) {
				if ( fromElementSpace.getRoot().resolveAttribute( attributeName ) != null ) {
					return fromElementSpace.getRoot();
				}

				for ( JoinedFromElement joinedFromElement : fromElementSpace.getJoins() ) {
					if ( joinedFromElement.resolveAttribute( attributeName ) != null ) {
						return joinedFromElement;
					}
				}
			}

			// otherwise there is none
			return null;
		}

		@Override
		public FromElementLocator getFromElementLocator() {
			return this;
		}

		@Override
		public FromElementBuilder getFromElementBuilder() {
			throw new SemanticException( "order-by clause cannot define implicit joins" );
		}

		@Override
		public ParsingContext getParsingContext() {
			return parsingContext;
		}
	}

	@Override
	public OrderByClause visitOrderByClause(HqlParser.OrderByClauseContext ctx) {
		final OrderByClause orderByClause = new OrderByClause();
		for ( HqlParser.SortSpecificationContext sortSpecificationContext : ctx.sortSpecification() ) {
			orderByClause.addSortSpecification( visitSortSpecification( sortSpecificationContext ) );
		}
		return orderByClause;
	}

	@Override
	public SortSpecification visitSortSpecification(HqlParser.SortSpecificationContext ctx) {
		final SqmExpression sortExpression = (SqmExpression) ctx.expression().accept( this );
		final String collation;
		if ( ctx.collationSpecification() != null && ctx.collationSpecification().collateName() != null ) {
			collation = ctx.collationSpecification().collateName().dotIdentifierSequence().getText();
		}
		else {
			collation = null;
		}
		final SortOrder sortOrder;
		if ( ctx.orderingSpecification() != null ) {
			final String ordering = ctx.orderingSpecification().getText();
			try {
				sortOrder = interpretSortOrder( ordering );
			}
			catch (IllegalArgumentException e) {
				throw new SemanticException( "Unrecognized sort ordering: " + ordering, e );
			}
		}
		else {
			sortOrder = null;
		}
		return new SortSpecification( sortExpression, collation, sortOrder );
	}

	private SortOrder interpretSortOrder(String value) {
		if ( value == null ) {
			return null;
		}

		if ( value.equalsIgnoreCase( "ascending" ) || value.equalsIgnoreCase( "asc" ) ) {
			return SortOrder.ASCENDING;
		}

		if ( value.equalsIgnoreCase( "descending" ) || value.equalsIgnoreCase( "desc" ) ) {
			return SortOrder.DESCENDING;
		}

		throw new SemanticException( "Unknown sort order : " + value );
	}

	@Override
	public DeleteStatement visitDeleteStatement(HqlParser.DeleteStatementContext ctx) {
		currentQuerySpecProcessingState = new QuerySpecProcessingStateDmlImpl( parsingContext );
		try {
			final RootEntityFromElement root = resolveDmlRootEntityReference( ctx.mainEntityPersisterReference() );
			final DeleteStatement deleteStatement = new DeleteStatement( root );

			pathResolverStack.push( new PathResolverBasicImpl( currentQuerySpecProcessingState ) );
			try {
				deleteStatement.getWhereClause().setPredicate( (SqmPredicate) ctx.whereClause()
						.predicate()
						.accept( this ) );
			}
			finally {
				pathResolverStack.pop();
			}

			return deleteStatement;
		}
		finally {
			currentQuerySpecProcessingState = null;
		}
	}

	protected RootEntityFromElement resolveDmlRootEntityReference(HqlParser.MainEntityPersisterReferenceContext rootEntityContext) {
		final EntityType entityType = resolveEntityReference( rootEntityContext.dotIdentifierSequence() );
		String alias = interpretIdentificationVariable( rootEntityContext.identificationVariableDef() );
		if ( alias == null ) {
			alias = parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
			log.debugf(
					"Generated implicit alias [%s] for DML root entity reference [%s]",
					alias,
					entityType.getName()
			);
		}
		final RootEntityFromElement root = new RootEntityFromElement( null, parsingContext.makeUniqueIdentifier(), alias, entityType );
		parsingContext.registerFromElementByUniqueId( root );
		currentQuerySpecProcessingState.getFromElementBuilder().getAliasRegistry().registerAlias( root );
		currentQuerySpecProcessingState.getFromClause().getFromElementSpaces().get( 0 ).setRoot( root );
		return root;
	}

	private String interpretIdentificationVariable(HqlParser.IdentificationVariableDefContext identificationVariableDef) {
		if ( identificationVariableDef != null ) {
			final String explicitAlias;
			if ( identificationVariableDef.AS() != null ) {
				final Token identificationVariableToken = identificationVariableDef.identificationVariable().identifier().getStart();
				if ( identificationVariableToken.getType() != HqlParser.IDENTIFIER ) {
					// we have a reserved word used as an identification variable.
					if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
						throw new StrictJpaComplianceViolation(
								String.format(
										Locale.ROOT,
										"Strict JPQL compliance was violated : %s [%s]",
										StrictJpaComplianceViolation.Type.RESERVED_WORD_USED_AS_ALIAS.description(),
										identificationVariableToken.getText()
								),
								StrictJpaComplianceViolation.Type.RESERVED_WORD_USED_AS_ALIAS
						);
					}
				}
				explicitAlias = identificationVariableToken.getText();
			}
			else {
				explicitAlias = identificationVariableDef.IDENTIFIER().getText();
			}
			return explicitAlias;
		}

		return parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
	}

	@Override
	public UpdateStatement visitUpdateStatement(HqlParser.UpdateStatementContext ctx) {
		currentQuerySpecProcessingState = new QuerySpecProcessingStateDmlImpl( parsingContext );
		try {
			final RootEntityFromElement root = resolveDmlRootEntityReference( ctx.mainEntityPersisterReference() );
			final UpdateStatement updateStatement = new UpdateStatement( root );

			pathResolverStack.push( new PathResolverBasicImpl( currentQuerySpecProcessingState ) );
			try {
				updateStatement.getWhereClause().setPredicate(
						(SqmPredicate) ctx.whereClause().predicate().accept( this )
				);

				for ( HqlParser.AssignmentContext assignmentContext : ctx.setClause().assignment() ) {
					final AttributeReferenceSqmExpression stateField = (AttributeReferenceSqmExpression) pathResolverStack.getCurrent().resolvePath(
							splitPathParts( assignmentContext.dotIdentifierSequence() )
					);
					// todo : validate "state field" expression
					updateStatement.getSetClause().addAssignment(
							stateField,
							(SqmExpression) assignmentContext.expression().accept( this )
					);
				}
			}
			finally {
				pathResolverStack.pop();
			}

			return updateStatement;
		}
		finally {
			currentQuerySpecProcessingState = null;
		}
	}

	private String[] splitPathParts(HqlParser.DotIdentifierSequenceContext path) {
		final String pathText = path.getText();
		log.debugf( "Splitting dotIdentifierSequence into path parts : %s", pathText );
		return PathHelper.split( pathText );
	}

	@Override
	public InsertSelectStatement visitInsertStatement(HqlParser.InsertStatementContext ctx) {
		currentQuerySpecProcessingState = new QuerySpecProcessingStateDmlImpl( parsingContext );
		try {
			final EntityType entityType = resolveEntityReference( ctx.insertSpec().intoSpec().dotIdentifierSequence() );
			String alias = parsingContext.getImplicitAliasGenerator().buildUniqueImplicitAlias();
			log.debugf(
					"Generated implicit alias [%s] for INSERT target [%s]",
					alias,
					entityType.getName()
			);

			RootEntityFromElement root = new RootEntityFromElement( null, parsingContext.makeUniqueIdentifier(), alias, entityType );
			parsingContext.registerFromElementByUniqueId( root );
			currentQuerySpecProcessingState.getFromElementBuilder().getAliasRegistry().registerAlias( root );
			currentQuerySpecProcessingState.getFromClause().getFromElementSpaces().get( 0 ).setRoot( root );

			// for now we only support the INSERT-SELECT form
			final InsertSelectStatement insertStatement = new InsertSelectStatement( root );

			pathResolverStack.push( new PathResolverBasicImpl( currentQuerySpecProcessingState ) );
			try {
				insertStatement.setSelectQuery( visitQuerySpec( ctx.querySpec() ) );

				for ( HqlParser.DotIdentifierSequenceContext stateFieldCtx : ctx.insertSpec().targetFieldsSpec().dotIdentifierSequence() ) {
					final AttributeReferenceSqmExpression stateField = (AttributeReferenceSqmExpression) pathResolverStack.getCurrent().resolvePath(
							splitPathParts( stateFieldCtx )
					);
					// todo : validate each resolved stateField...
					insertStatement.addInsertTargetStateField( stateField );
				}
			}
			finally {
				pathResolverStack.pop();
			}

			return insertStatement;
		}
		finally {
			currentQuerySpecProcessingState = null;
		}
	}

	private FromElementSpace currentFromElementSpace;

	@Override
	public Object visitFromElementSpace(HqlParser.FromElementSpaceContext ctx) {
		currentFromElementSpace = currentQuerySpecProcessingState.getFromClause().makeFromElementSpace();

		// adding root and joins to the FromElementSpace is currently handled in FromElementBuilder
		// it is very questionable whether this should be done there, but for now keep it
		// todo : revisit ^^

		visitFromElementSpaceRoot( ctx.fromElementSpaceRoot() );

		for ( HqlParser.CrossJoinContext crossJoinContext : ctx.crossJoin() ) {
			visitCrossJoin( crossJoinContext );
		}

		for ( HqlParser.QualifiedJoinContext qualifiedJoinContext : ctx.qualifiedJoin() ) {
			visitQualifiedJoin( qualifiedJoinContext );
		}

		for ( HqlParser.JpaCollectionJoinContext jpaCollectionJoinContext : ctx.jpaCollectionJoin() ) {
			visitJpaCollectionJoin( jpaCollectionJoinContext );
		}


		FromElementSpace rtn = currentFromElementSpace;
		currentFromElementSpace = null;
		return rtn;
	}

	@Override
	public RootEntityFromElement visitFromElementSpaceRoot(HqlParser.FromElementSpaceRootContext ctx) {
		final EntityType entityType = resolveEntityReference(
				ctx.mainEntityPersisterReference().dotIdentifierSequence()
		);

		if ( PolymorphicEntityType.class.isInstance( entityType ) ) {
			if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
				throw new StrictJpaComplianceViolation(
						"Encountered unmapped polymorphic reference [" + entityType.getName()
								+ "], but strict JPQL compliance was requested",
						StrictJpaComplianceViolation.Type.UNMAPPED_POLYMORPHISM
				);
			}

			// todo : disallow in subqueries as well
		}

		return currentQuerySpecProcessingState.getFromElementBuilder().makeRootEntityFromElement(
				currentFromElementSpace,
				entityType,
				interpretIdentificationVariable( ctx.mainEntityPersisterReference().identificationVariableDef() )
		);
	}

	private EntityType resolveEntityReference(HqlParser.DotIdentifierSequenceContext dotIdentifierSequenceContext) {
		final String entityName = dotIdentifierSequenceContext.getText();
		final EntityType entityTypeDescriptor = parsingContext.getConsumerContext()
				.getDomainMetamodel()
				.resolveEntityType( entityName );
		if ( entityTypeDescriptor == null ) {
			throw new SemanticException( "Unresolved entity name : " + entityName );
		}
		return entityTypeDescriptor;
	}

	@Override
	public CrossJoinedFromElement visitCrossJoin(HqlParser.CrossJoinContext ctx) {
		final EntityType entityType = resolveEntityReference(
				ctx.mainEntityPersisterReference().dotIdentifierSequence()
		);

		if ( PolymorphicEntityType.class.isInstance( entityType ) ) {
			throw new SemanticException(
					"Unmapped polymorphic references are only valid as sqm root, not in cross join : " +
							entityType.getName()
			);
		}

		return currentQuerySpecProcessingState.getFromElementBuilder().makeCrossJoinedFromElement(
				currentFromElementSpace,
				parsingContext.makeUniqueIdentifier(),
				entityType,
				interpretIdentificationVariable( ctx.mainEntityPersisterReference().identificationVariableDef() )
		);
	}

	@Override
	public QualifiedJoinedFromElement visitJpaCollectionJoin(HqlParser.JpaCollectionJoinContext ctx) {
		pathResolverStack.push(
				new PathResolverJoinAttributeImpl(
						currentQuerySpecProcessingState,
						currentFromElementSpace,
						JoinType.INNER,
						interpretIdentificationVariable( ctx.identificationVariableDef() ),
						false
				)
		);

		try {
			QualifiedJoinedFromElement joinedPath = (QualifiedJoinedFromElement) ctx.path().accept( this );

			if ( joinedPath == null ) {
				throw new ParsingException( "Could not resolve JPA collection join path : " + ctx.getText() );
			}

			return joinedPath;
		}
		finally {
			pathResolverStack.pop();
		}
	}

	@Override
	public QualifiedJoinedFromElement visitQualifiedJoin(HqlParser.QualifiedJoinContext ctx) {
		final JoinType joinType;
		if ( ctx.OUTER() != null ) {
			// for outer joins, only left outer joins are currently supported
			if ( ctx.FULL() != null ) {
				throw new SemanticException( "FULL OUTER joins are not yet supported : " + ctx.getText() );
			}
			if ( ctx.RIGHT() != null ) {
				throw new SemanticException( "FULL OUTER joins are not yet supported : " + ctx.getText() );
			}

			joinType = JoinType.LEFT;
		}
		else {
			joinType = JoinType.INNER;
		}

		final String identificationVariable = interpretIdentificationVariable(
				ctx.qualifiedJoinRhs().identificationVariableDef()
		);

		pathResolverStack.push(
				new PathResolverJoinAttributeImpl(
						currentQuerySpecProcessingState,
						currentFromElementSpace,
						joinType,
						identificationVariable,
						ctx.FETCH() != null
				)
		);

		try {
			final QualifiedJoinedFromElement joinedFromElement;

			// Object because join-target might be either an EntityTypeExpression (... join Address a on ...)
			// or an attribute-join (... from p.address a on ...)
			final Object joinedPath = ctx.qualifiedJoinRhs().path().accept( this );
			if ( joinedPath instanceof QualifiedJoinedFromElement ) {
				joinedFromElement = (QualifiedJoinedFromElement) joinedPath;
			}
			else if ( joinedPath instanceof EntityTypeSqmExpression ) {
				joinedFromElement = currentQuerySpecProcessingState.getFromElementBuilder().buildEntityJoin(
						currentFromElementSpace,
						identificationVariable,
						( (EntityTypeSqmExpression) joinedPath ).getExpressionType(),
						joinType
				);
			}
			else {
				throw new ParsingException( "Unexpected qualifiedJoin.path resolution type : " + joinedPath );
			}

			currentJoinRhs = joinedFromElement;

			if ( joinedPath == null ) {
				throw new ParsingException( "Could not resolve join path : " + ctx.qualifiedJoinRhs().getText() );
			}

			if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
				if ( !ImplicitAliasGenerator.isImplicitAlias( joinedFromElement.getIdentificationVariable() ) ) {
					if ( QualifiedAttributeJoinFromElement.class.isInstance( joinedPath ) ) {
						if ( QualifiedAttributeJoinFromElement.class.cast( joinedPath ).isFetched() ) {
							throw new StrictJpaComplianceViolation(
									"Encountered aliased fetch join, but strict JPQL compliance was requested",
									StrictJpaComplianceViolation.Type.ALIASED_FETCH_JOIN
							);
						}
					}
				}
			}

			if ( ctx.qualifiedJoinPredicate() != null ) {
				joinedFromElement.setOnClausePredicate( visitQualifiedJoinPredicate( ctx.qualifiedJoinPredicate() ) );
			}

			return joinedFromElement;
		}
		finally {
			currentJoinRhs = null;
			pathResolverStack.pop();
		}
	}

	private QualifiedJoinedFromElement currentJoinRhs;

	@Override
	public SqmPredicate visitQualifiedJoinPredicate(HqlParser.QualifiedJoinPredicateContext ctx) {
		if ( currentJoinRhs == null ) {
			throw new ParsingException( "Expecting join RHS to be set" );
		}

		pathResolverStack.push(
				new PathResolverJoinPredicateImpl( currentQuerySpecProcessingState, currentJoinRhs )
		);
		try {
			return (SqmPredicate) ctx.predicate().accept( this );
		}
		finally {

			pathResolverStack.pop();
		}
	}
	@Override
	public SqmPredicate visitAndPredicate(HqlParser.AndPredicateContext ctx) {
		return new AndSqmPredicate(
				(SqmPredicate) ctx.predicate( 0 ).accept( this ),
				(SqmPredicate) ctx.predicate( 1 ).accept( this )
		);
	}

	@Override
	public SqmPredicate visitOrPredicate(HqlParser.OrPredicateContext ctx) {
		return new OrSqmPredicate(
				(SqmPredicate) ctx.predicate( 0 ).accept( this ),
				(SqmPredicate) ctx.predicate( 1 ).accept( this )
		);
	}

	@Override
	public SqmPredicate visitNegatedPredicate(HqlParser.NegatedPredicateContext ctx) {
		SqmPredicate predicate = (SqmPredicate) ctx.predicate().accept( this );
		if ( predicate instanceof NegatableSqmPredicate ) {
			( (NegatableSqmPredicate) predicate ).negate();
			return predicate;
		}
		else {
			return new NegatedSqmPredicate( predicate );
		}
	}

	@Override
	public NullnessSqmPredicate visitIsNullPredicate(HqlParser.IsNullPredicateContext ctx) {
		return new NullnessSqmPredicate(
				(SqmExpression) ctx.expression().accept( this ),
				ctx.NOT() != null
		);
	}

	@Override
	public EmptinessSqmPredicate visitIsEmptyPredicate(HqlParser.IsEmptyPredicateContext ctx) {
		return new EmptinessSqmPredicate(
				(SqmExpression) ctx.expression().accept( this ),
				ctx.NOT() != null
		);
	}

	@Override
	public RelationalSqmPredicate visitEqualityPredicate(HqlParser.EqualityPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.EQUAL, lhs, rhs );
	}

	@Override
	public Object visitInequalityPredicate(HqlParser.InequalityPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.NOT_EQUAL, lhs, rhs );
	}

	@Override
	public Object visitGreaterThanPredicate(HqlParser.GreaterThanPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.GREATER_THAN, lhs, rhs );
	}

	@Override
	public Object visitGreaterThanOrEqualPredicate(HqlParser.GreaterThanOrEqualPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.GREATER_THAN_OR_EQUAL, lhs, rhs );
	}

	@Override
	public Object visitLessThanPredicate(HqlParser.LessThanPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.LESS_THAN, lhs, rhs );
	}

	@Override
	public Object visitLessThanOrEqualPredicate(HqlParser.LessThanOrEqualPredicateContext ctx) {
		final SqmExpression lhs = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression rhs = (SqmExpression) ctx.expression().get( 1 ).accept( this );

		if ( lhs.getInferableType() != null ) {
			if ( rhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) rhs ).impliedType( lhs.getInferableType() );
			}
		}

		if ( rhs.getInferableType() != null ) {
			if ( lhs instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lhs ).impliedType( rhs.getInferableType() );
			}
		}

		return new RelationalSqmPredicate( RelationalSqmPredicate.Operator.LESS_THAN_OR_EQUAL, lhs, rhs );
	}

	@Override
	public Object visitBetweenPredicate(HqlParser.BetweenPredicateContext ctx) {
		final SqmExpression expression = (SqmExpression) ctx.expression().get( 0 ).accept( this );
		final SqmExpression lowerBound = (SqmExpression) ctx.expression().get( 1 ).accept( this );
		final SqmExpression upperBound = (SqmExpression) ctx.expression().get( 2 ).accept( this );

		if ( expression.getInferableType() != null ) {
			if ( lowerBound instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lowerBound ).impliedType( expression.getInferableType() );
			}
			if ( upperBound instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) upperBound ).impliedType( expression.getInferableType() );
			}
		}
		else if ( lowerBound.getInferableType() != null ) {
			if ( expression instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) expression ).impliedType( lowerBound.getInferableType() );
			}
			if ( upperBound instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) upperBound ).impliedType( lowerBound.getInferableType() );
			}
		}
		else if ( upperBound.getInferableType() != null ) {
			if ( expression instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) expression ).impliedType( upperBound.getInferableType() );
			}
			if ( lowerBound instanceof ImpliedTypeSqmExpression ) {
				( (ImpliedTypeSqmExpression) lowerBound ).impliedType( upperBound.getInferableType() );
			}
		}

		return new BetweenSqmPredicate(
				expression,
				lowerBound,
				upperBound,
				false
		);
	}

	@Override
	public Object visitLikePredicate(HqlParser.LikePredicateContext ctx) {
		if ( ctx.likeEscape() != null ) {
			return new LikeSqmPredicate(
					(SqmExpression) ctx.expression().get( 0 ).accept( this ),
					(SqmExpression) ctx.expression().get( 1 ).accept( this ),
					(SqmExpression) ctx.likeEscape().expression().accept( this )
			);
		}
		else {
			return new LikeSqmPredicate(
					(SqmExpression) ctx.expression().get( 0 ).accept( this ),
					(SqmExpression) ctx.expression().get( 1 ).accept( this )
			);
		}
	}

	@Override
	public Object visitMemberOfPredicate(HqlParser.MemberOfPredicateContext ctx) {
		final Object pathResolution = ctx.path().accept( this );
		if ( !AttributeReferenceSqmExpression.class.isInstance( pathResolution ) ) {
			throw new SemanticException( "Could not resolve path [" + ctx.path().getText() + "] as an attribute reference" );
		}
		final AttributeReferenceSqmExpression attributeReference = (AttributeReferenceSqmExpression) pathResolution;
		if ( !PluralAttribute.class.isInstance( attributeReference.getBoundAttribute() ) ) {
			throw new SemanticException( "Path argument to MEMBER OF must be a collection" );
		}
		return new MemberOfSqmPredicate( attributeReference );
	}

	@Override
	public Object visitInPredicate(HqlParser.InPredicateContext ctx) {
		final SqmExpression testExpression = (SqmExpression) ctx.expression().accept( this );

		if ( HqlParser.ExplicitTupleInListContext.class.isInstance( ctx.inList() ) ) {
			final HqlParser.ExplicitTupleInListContext tupleExpressionListContext = (HqlParser.ExplicitTupleInListContext) ctx.inList();
			final List<SqmExpression> listExpressions = new ArrayList<SqmExpression>( tupleExpressionListContext.expression().size() );
			for ( HqlParser.ExpressionContext expressionContext : tupleExpressionListContext.expression() ) {
				final SqmExpression listItemExpression = (SqmExpression) expressionContext.accept( this );

				if ( testExpression.getInferableType() != null ) {
					if ( listItemExpression instanceof ImpliedTypeSqmExpression ) {
						( (ImpliedTypeSqmExpression) listItemExpression ).impliedType( testExpression.getInferableType() );
					}
				}

				listExpressions.add( listItemExpression );
			}

			return new InListSqmPredicate( testExpression, listExpressions );
		}
		else if ( HqlParser.SubQueryInListContext.class.isInstance( ctx.inList() ) ) {
			final HqlParser.SubQueryInListContext subQueryContext = (HqlParser.SubQueryInListContext) ctx.inList();
			final SqmExpression subQueryExpression = (SqmExpression) subQueryContext.expression().accept( this );

			if ( !SubQuerySqmExpression.class.isInstance( subQueryExpression ) ) {
				throw new ParsingException(
						"Was expecting a SubQueryExpression, but found " + subQueryExpression.getClass().getSimpleName()
								+ " : " + subQueryContext.expression().toString()
				);
			}

			return new InSubQuerySqmPredicate( testExpression, (SubQuerySqmExpression) subQueryExpression );
		}

		// todo : handle PersistentCollectionReferenceInList labeled branch

		throw new ParsingException( "Unexpected IN predicate type [" + ctx.getClass().getSimpleName() + "] : " + ctx.getText() );
	}

	@Override
	public Object visitSimplePath(HqlParser.SimplePathContext ctx) {
		// SimplePath might represent any number of things
		final Binding binding = pathResolverStack.getCurrent().resolvePath( splitPathParts( ctx.dotIdentifierSequence() ) );
		if ( binding != null ) {
			return binding;
		}

		final String pathText = ctx.getText();

		try {
			final EntityType entityType = parsingContext.getConsumerContext().getDomainMetamodel().resolveEntityType( pathText );
			if ( entityType != null ) {
				return new EntityTypeSqmExpression( entityType );
			}
		}
		catch (IllegalArgumentException ignore) {
		}

		// 5th level precedence : constant reference
		try {
			return resolveConstantExpression( pathText );
		}
		catch (SemanticException e) {
			log.debug( e.getMessage() );
		}

		// if we get here we had a problem interpreting the dot-ident sequence
		throw new SemanticException( "Could not interpret token : " + pathText );
	}

	@Override
	public MapEntrySqmFunction visitMapEntryPath(HqlParser.MapEntryPathContext ctx) {
		final Binding pathResolution = (Binding) ctx.mapReference().path().accept( this );

		if ( inWhereClause ) {
			throw new SemanticException(
					"entry() function may only be used in SELECT clauses; specified "
							+ "path [" + ctx.mapReference().path().getText() + "] is used in WHERE clause" );
		}

		if ( PluralAttribute.class.isInstance( pathResolution.getBoundModelType() ) ) {
			final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
			if ( pluralAttribute.getCollectionClassification() == PluralAttribute.CollectionClassification.MAP ) {
				return new MapEntrySqmFunction(
						pathResolution.getBoundFromElementBinding().getFromElement(),
						pluralAttribute.getIndexType(),
						pluralAttribute.getElementType()
				);
			}
		}

		throw new SemanticException(
				"entry() function can only be applied to path expressions which resolve to a persistent Map; specified "
						+ "path [" + ctx.mapReference().path().getText() + "] resolved to " + pathResolution.getBoundModelType()
		);
	}

	@Override
	public Binding visitIndexedPath(HqlParser.IndexedPathContext ctx) {
		final AttributeBinding pluralAttributeBinding = (AttributeBinding) ctx.path().accept( this );
		final SqmExpression indexExpression = (SqmExpression) ctx.expression().accept( this );

		// the source TypeDescriptor needs to be an indexed collection for this to be valid...
		if ( !PluralAttribute.class.isInstance( pluralAttributeBinding.getBoundModelType() ) ) {
			throw new SemanticException(
					"Index operator only valid for indexed collections (maps, lists, arrays) : " +
							pluralAttributeBinding.getBoundModelType()
			);
		}

		final PluralAttribute pluralAttribute = (PluralAttribute) pluralAttributeBinding.getBoundModelType();
		// todo : would be nice to validate the index's type against the Collection-index's type
		// 		but that requires "compatible type checking" rather than TypeDescriptor sameness (long versus int, e.g)

		final PluralAttributeIndexedReference indexedReference = new PluralAttributeIndexedReference(
				pluralAttributeBinding,
				indexExpression,
				// Ultimately the Type for this Expression is the same as the elements of the collection...
				pluralAttribute.getElementType()
		);

		if ( ctx.pathTerminal() == null ) {
			return indexedReference;
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// otherwise, we have a dereference of the pathRoot (as a pathTerminal)

		// the binding would additionally need to be an AttributeBindingSource
		// and expose a Bindable
		if ( indexedReference.getBoundModelType() == null ) {
			throw new SemanticException(
					String.format(
							Locale.ROOT,
							"path root [%s] did not resolve to an attribute source",
							ctx.path().getText()
					)
			);
		}

		return pathResolverStack.getCurrent().resolvePath(
				indexedReference,
				PathHelper.split( ctx.pathTerminal().getText() )
		);
	}

	@Override
	public Binding visitCompoundPath(HqlParser.CompoundPathContext ctx) {
		final Binding root = (Binding) ctx.pathRoot().accept( this );

		log.debugf(
				"Resolved CompoundPath.pathRoot [%s] : %s",
				ctx.pathRoot().getText(),
				root.asLoggableText()
		);

		if ( ctx.pathTerminal() == null ) {
			return root;
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// otherwise, we have a dereference of the pathRoot (as a pathTerminal)

		// the binding would additionally need to be an AttributeBindingSource
		// and expose a Bindable
		if ( !AttributeBindingSource.class.isInstance( root )
				|| root.getBoundModelType() == null ) {
			throw new SemanticException(
					String.format(
							Locale.ROOT,
							"path root [%s] did not resolve to an attribute source",
							ctx.pathRoot().getText()
					)
			);
		}
		final AttributeBindingSource attributeBindingSource = (AttributeBindingSource) root;

		return pathResolverStack.getCurrent().resolvePath(
				attributeBindingSource,
				PathHelper.split( ctx.pathTerminal().getText() )
		);
	}

	@Override
	public Object visitMapKeyPathRoot(HqlParser.MapKeyPathRootContext ctx) {
		final Binding pathResolution = (Binding) ctx.mapReference().path().accept( this );

		final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
		if ( pluralAttribute.getCollectionClassification() != PluralAttribute.CollectionClassification.MAP ) {
			throw new SemanticException(
					"key() function can only be applied to path expressions which resolve to a persistent Map; " +
							"specified path [" + ctx.mapReference().path().getText() + "] resolved to " + pathResolution.getBoundModelType()
			);
		}

		return new MapKeyPathSqmExpression( pathResolution.getBoundFromElementBinding().getFromElement(), pluralAttribute.getIndexType() );
	}

	@Override
	public CollectionValuePathSqmExpression visitCollectionValuePathRoot(HqlParser.CollectionValuePathRootContext ctx) {
		final Binding pathResolution = visitCollectionReference( ctx.collectionReference() );
		if ( !QualifiedAttributeJoinFromElement.class.isInstance( pathResolution ) ) {
			throw new SemanticException(
					"value() function can only be applied to path expressions which resolve to a plural attribute; specified " +
							"path [" + ctx.collectionReference().path().getText() + "] resolved to " + pathResolution.getClass().getName()
			);
		}

		final QualifiedAttributeJoinFromElement attributeBinding = (QualifiedAttributeJoinFromElement) pathResolution;
		if ( !PluralAttribute.class.isInstance( attributeBinding.getBoundAttribute() ) ) {
			throw new SemanticException(
					"value() function can only be applied to path expressions which resolve to a collection; specified " +
							"path [" + ctx.collectionReference().path().getText() + "] resolved to " + pathResolution
			);
		}

		final PluralAttribute collectionReference = (PluralAttribute) attributeBinding.getBoundAttribute();
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			if ( collectionReference.getCollectionClassification() != PluralAttribute.CollectionClassification.MAP ) {
				throw new StrictJpaComplianceViolation(
						"Encountered application of value() function to path expression which does not " +
								"resolve to a persistent Map, but strict JPQL compliance was requested. specified "
								+ "path [" + ctx.collectionReference().path().getText() + "] resolved to " + pathResolution,
						StrictJpaComplianceViolation.Type.VALUE_FUNCTION_ON_NON_MAP
				);
			}
		}

		return new CollectionValuePathSqmExpression(
				attributeBinding,
				collectionReference.getElementType()
		);
	}

	@Override
	public Binding visitCollectionReference(HqlParser.CollectionReferenceContext ctx) {
		return toPluralAttributeBinding( ctx.path() );
	}

	protected Binding toPluralAttributeBinding(HqlParser.PathContext ctx) {
		final Binding binding = (Binding) ctx.accept( this );
		if ( !PluralAttribute.class.isInstance( binding.getBoundModelType() ) ) {
			throw new SemanticException(
					"Expecting a collection (plural attribute) reference, but specified path [" +
							ctx.getText() + "] resolved to " + binding
			);
		}

		return binding;
	}

	@Override
	public Binding visitMapReference(HqlParser.MapReferenceContext ctx) {
		final Binding pathResolution = toPluralAttributeBinding( ctx.path() );
		final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
		if ( pluralAttribute.getCollectionClassification() != PluralAttribute.CollectionClassification.MAP ) {
			throw new SemanticException(
					"Expecting a persistent-Map (plural attribute) reference, but specified path [" +
							ctx.path().getText() + "] resolved to " + pathResolution
			);
		}

		return pathResolution;
	}

	@Override
	public Binding visitTreatedPathRoot(HqlParser.TreatedPathRootContext ctx) {
		final String treatAsName = ctx.dotIdentifierSequence().get( 1 ).getText();
		final EntityType treatAsTypeDescriptor = parsingContext.getConsumerContext()
				.getDomainMetamodel()
				.resolveEntityType( treatAsName );
		if ( treatAsTypeDescriptor == null ) {
			throw new SemanticException( "TREAT-AS target type [" + treatAsName + "] did not reference an entity" );
		}

		return pathResolverStack.getCurrent().resolvePath(
				treatAsTypeDescriptor,
				splitPathParts( ctx.dotIdentifierSequence().get( 0 ) )
		);
	}

	@SuppressWarnings("unchecked")
	protected ConstantSqmExpression resolveConstantExpression(String reference) {
		// todo : hook in "import" resolution using the ParsingContext
		final int dotPosition = reference.lastIndexOf( '.' );
		final String className = reference.substring( 0, dotPosition - 1 );
		final String fieldName = reference.substring( dotPosition+1, reference.length() );

		try {
			final Class clazz = parsingContext.getConsumerContext().classByName( className );
			if ( clazz.isEnum() ) {
				try {
					return new ConstantEnumSqmExpression(
							Enum.valueOf( clazz, fieldName ),
							parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( clazz )
					);
				}
				catch (IllegalArgumentException e) {
					throw new SemanticException( "Name [" + fieldName + "] does not represent an enum constant on enum class [" + className + "]" );
				}
			}
			else {
				try {
					final Field field = clazz.getField( fieldName );
					if ( !Modifier.isStatic( field.getModifiers() ) ) {
						throw new SemanticException( "Field [" + fieldName + "] is not static on class [" + className + "]" );
					}
					field.setAccessible( true );
					return new ConstantFieldSqmExpression(
							field.get( null ),
							parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( field.getType() )
					);
				}
				catch (NoSuchFieldException e) {
					throw new SemanticException( "Name [" + fieldName + "] does not represent a field on class [" + className + "]", e );
				}
				catch (SecurityException e) {
					throw new SemanticException( "Field [" + fieldName + "] is not accessible on class [" + className + "]", e );
				}
				catch (IllegalAccessException e) {
					throw new SemanticException( "Unable to access field [" + fieldName + "] on class [" + className + "]", e );
				}
			}
		}
		catch (ClassNotFoundException e) {
			throw new SemanticException( "Cannot resolve class for sqm constant [" + reference + "]" );
		}
	}

	@Override
	public ConcatSqmExpression visitConcatenationExpression(HqlParser.ConcatenationExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the concat operator" );
		}
		return new ConcatSqmExpression(
				(SqmExpression) ctx.expression( 0 ).accept( this ),
				(SqmExpression) ctx.expression( 1 ).accept( this )
		);
	}

	@Override
	public Object visitAdditionExpression(HqlParser.AdditionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the + operator" );
		}

		final SqmExpression firstOperand = (SqmExpression) ctx.expression( 0 ).accept( this );
		final SqmExpression secondOperand = (SqmExpression) ctx.expression( 1 ).accept( this );
		return new BinaryArithmeticSqmExpression(
				BinaryArithmeticSqmExpression.Operation.ADD,
				firstOperand,
				secondOperand,
				ExpressionTypeHelper.resolveArithmeticType(
						(BasicType) firstOperand.getExpressionType(),
						(BasicType) secondOperand.getExpressionType(),
						parsingContext.getConsumerContext(),
						false
				)
		);
	}

	@Override
	public Object visitSubtractionExpression(HqlParser.SubtractionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the - operator" );
		}

		final SqmExpression firstOperand = (SqmExpression) ctx.expression( 0 ).accept( this );
		final SqmExpression secondOperand = (SqmExpression) ctx.expression( 1 ).accept( this );
		return new BinaryArithmeticSqmExpression(
				BinaryArithmeticSqmExpression.Operation.SUBTRACT,
				firstOperand,
				secondOperand,
				ExpressionTypeHelper.resolveArithmeticType(
						(BasicType) firstOperand.getExpressionType(),
						(BasicType) secondOperand.getExpressionType(),
						parsingContext.getConsumerContext(),
						false
				)
		);
	}

	@Override
	public Object visitMultiplicationExpression(HqlParser.MultiplicationExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the * operator" );
		}

		final SqmExpression firstOperand = (SqmExpression) ctx.expression( 0 ).accept( this );
		final SqmExpression secondOperand = (SqmExpression) ctx.expression( 1 ).accept( this );
		return new BinaryArithmeticSqmExpression(
				BinaryArithmeticSqmExpression.Operation.MULTIPLY,
				firstOperand,
				secondOperand,
				ExpressionTypeHelper.resolveArithmeticType(
						(BasicType) firstOperand.getExpressionType(),
						(BasicType) secondOperand.getExpressionType(),
						parsingContext.getConsumerContext(),
						false
				)
		);
	}

	@Override
	public Object visitDivisionExpression(HqlParser.DivisionExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the / operator" );
		}

		final SqmExpression firstOperand = (SqmExpression) ctx.expression( 0 ).accept( this );
		final SqmExpression secondOperand = (SqmExpression) ctx.expression( 1 ).accept( this );
		return new BinaryArithmeticSqmExpression(
				BinaryArithmeticSqmExpression.Operation.DIVIDE,
				firstOperand,
				secondOperand,
				ExpressionTypeHelper.resolveArithmeticType(
						(BasicType) firstOperand.getExpressionType(),
						(BasicType) secondOperand.getExpressionType(),
						parsingContext.getConsumerContext(),
						true
				)
		);
	}

	@Override
	public Object visitModuloExpression(HqlParser.ModuloExpressionContext ctx) {
		if ( ctx.expression().size() != 2 ) {
			throw new ParsingException( "Expecting 2 operands to the % operator" );
		}

		final SqmExpression firstOperand = (SqmExpression) ctx.expression( 0 ).accept( this );
		final SqmExpression secondOperand = (SqmExpression) ctx.expression( 1 ).accept( this );
		return new BinaryArithmeticSqmExpression(
				BinaryArithmeticSqmExpression.Operation.MODULO,
				firstOperand,
				secondOperand,
				ExpressionTypeHelper.resolveArithmeticType(
						(BasicType) firstOperand.getExpressionType(),
						(BasicType) secondOperand.getExpressionType(),
						parsingContext.getConsumerContext(),
						false
				)
		);
	}

	@Override
	public Object visitUnaryPlusExpression(HqlParser.UnaryPlusExpressionContext ctx) {
		return new UnaryOperationSqmExpression(
				UnaryOperationSqmExpression.Operation.PLUS,
				(SqmExpression) ctx.expression().accept( this )
		);
	}

	@Override
	public Object visitUnaryMinusExpression(HqlParser.UnaryMinusExpressionContext ctx) {
		return new UnaryOperationSqmExpression(
				UnaryOperationSqmExpression.Operation.MINUS,
				(SqmExpression) ctx.expression().accept( this )
		);
	}

	@Override
	public CaseSimpleSqmExpression visitSimpleCaseStatement(HqlParser.SimpleCaseStatementContext ctx) {
		final CaseSimpleSqmExpression caseExpression = new CaseSimpleSqmExpression(
				(SqmExpression) ctx.expression().accept( this )
		);

		for ( HqlParser.SimpleCaseWhenContext simpleCaseWhen : ctx.simpleCaseWhen() ) {
			caseExpression.when(
					(SqmExpression) simpleCaseWhen.expression( 0 ).accept( this ),
					(SqmExpression) simpleCaseWhen.expression( 0 ).accept( this )
			);
		}

		if ( ctx.caseOtherwise() != null ) {
			caseExpression.otherwise( (SqmExpression) ctx.caseOtherwise().expression().accept( this ) );
		}

		return caseExpression;
	}

	@Override
	public CaseSearchedSqmExpression visitSearchedCaseStatement(HqlParser.SearchedCaseStatementContext ctx) {
		final CaseSearchedSqmExpression caseExpression = new CaseSearchedSqmExpression();

		for ( HqlParser.SearchedCaseWhenContext whenFragment : ctx.searchedCaseWhen() ) {
			caseExpression.when(
					(SqmPredicate) whenFragment.predicate().accept( this ),
					(SqmExpression) whenFragment.expression().accept( this )
			);
		}

		if ( ctx.caseOtherwise() != null ) {
			caseExpression.otherwise( (SqmExpression) ctx.caseOtherwise().expression().accept( this ) );
		}

		return caseExpression;
	}

	@Override
	public CoalesceSqmExpression visitCoalesceExpression(HqlParser.CoalesceExpressionContext ctx) {
		CoalesceSqmExpression coalesceExpression = new CoalesceSqmExpression();
		for ( HqlParser.ExpressionContext expressionContext : ctx.coalesce().expression() ) {
			coalesceExpression.value( (SqmExpression) expressionContext.accept( this ) );
		}
		return coalesceExpression;
	}

	@Override
	public NullifSqmExpression visitNullIfExpression(HqlParser.NullIfExpressionContext ctx) {
		return new NullifSqmExpression(
				(SqmExpression) ctx.nullIf().expression( 0 ).accept( this ),
				(SqmExpression) ctx.nullIf().expression( 1 ).accept( this )
		);
	}

	@Override
	@SuppressWarnings("UnnecessaryBoxing")
	public LiteralSqmExpression visitLiteralExpression(HqlParser.LiteralExpressionContext ctx) {
		if ( ctx.literal().CHARACTER_LITERAL() != null ) {
			return characterLiteral( ctx.literal().CHARACTER_LITERAL().getText() );
		}
		else if ( ctx.literal().STRING_LITERAL() != null ) {
			return stringLiteral( ctx.literal().STRING_LITERAL().getText() );
		}
		else if ( ctx.literal().INTEGER_LITERAL() != null ) {
			return integerLiteral( ctx.literal().INTEGER_LITERAL().getText() );
		}
		else if ( ctx.literal().LONG_LITERAL() != null ) {
			return longLiteral( ctx.literal().LONG_LITERAL().getText() );
		}
		else if ( ctx.literal().BIG_INTEGER_LITERAL() != null ) {
			return bigIntegerLiteral( ctx.literal().BIG_INTEGER_LITERAL().getText() );
		}
		else if ( ctx.literal().HEX_LITERAL() != null ) {
			final String text = ctx.literal().HEX_LITERAL().getText();
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				return longLiteral( text );
			}
			else {
				return integerLiteral( text );
			}
		}
		else if ( ctx.literal().OCTAL_LITERAL() != null ) {
			final String text = ctx.literal().OCTAL_LITERAL().getText();
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				return longLiteral( text );
			}
			else {
				return integerLiteral( text );
			}
		}
		else if ( ctx.literal().FLOAT_LITERAL() != null ) {
			return floatLiteral( ctx.literal().FLOAT_LITERAL().getText() );
		}
		else if ( ctx.literal().DOUBLE_LITERAL() != null ) {
			return doubleLiteral( ctx.literal().DOUBLE_LITERAL().getText() );
		}
		else if ( ctx.literal().BIG_DECIMAL_LITERAL() != null ) {
			return bigDecimalLiteral( ctx.literal().BIG_DECIMAL_LITERAL().getText() );
		}
		else if ( ctx.literal().FALSE() != null ) {
			booleanLiteral( false );
		}
		else if ( ctx.literal().TRUE() != null ) {
			booleanLiteral( true );
		}
		else if ( ctx.literal().NULL() != null ) {
			return new LiteralNullSqmExpression();
		}

		// otherwise we have a problem
		throw new ParsingException( "Unexpected literal expression type [" + ctx.getText() + "]" );
	}

	private LiteralSqmExpression<Boolean> booleanLiteral(boolean value) {
		final BasicType<Boolean> type = parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Boolean.class );

		return value
				? new LiteralTrueSqmExpression( type )
				: new LiteralFalseSqmExpression( type );
	}

	private LiteralCharacterSqmExpression characterLiteral(String text) {
		if ( text.length() > 1 ) {
			// todo : or just treat it as a String literal?
			throw new ParsingException( "Value for CHARACTER_LITERAL token was more than 1 character" );
		}
		return new LiteralCharacterSqmExpression(
				text.charAt( 0 ),
				parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Character.class )
		);
	}

	private LiteralSqmExpression stringLiteral(String text) {
		return new LiteralStringSqmExpression(
				text,
				parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( String.class )
		);
	}

	protected LiteralIntegerSqmExpression integerLiteral(String text) {
		try {
			final Integer value = Integer.valueOf( text );
			return new LiteralIntegerSqmExpression(
					value,
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Integer.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + text + "] to Integer",
					e
			);
		}
	}

	protected LiteralLongSqmExpression longLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "l" ) || text.endsWith( "L" ) ) {
				text = text.substring( 0, text.length() - 1 );
			}
			final Long value = Long.valueOf( text );
			return new LiteralLongSqmExpression(
					value,
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Long.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + originalText + "] to Long",
					e
			);
		}
	}

	protected LiteralBigIntegerSqmExpression bigIntegerLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "bi" ) || text.endsWith( "BI" ) ) {
				text = text.substring( 0, text.length() - 2 );
			}
			return new LiteralBigIntegerSqmExpression(
					new BigInteger( text ),
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( BigInteger.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + originalText + "] to BigInteger",
					e
			);
		}
	}

	protected LiteralFloatSqmExpression floatLiteral(String text) {
		try {
			return new LiteralFloatSqmExpression(
					Float.valueOf( text ),
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Float.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + text + "] to Float",
					e
			);
		}
	}

	protected LiteralDoubleSqmExpression doubleLiteral(String text) {
		try {
			return new LiteralDoubleSqmExpression(
					Double.valueOf( text ),
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Double.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + text + "] to Double",
					e
			);
		}
	}

	protected LiteralBigDecimalSqmExpression bigDecimalLiteral(String text) {
		final String originalText = text;
		try {
			if ( text.endsWith( "bd" ) || text.endsWith( "BD" ) ) {
				text = text.substring( 0, text.length() - 2 );
			}
			return new LiteralBigDecimalSqmExpression(
					new BigDecimal( text ),
					parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( BigDecimal.class )
			);
		}
		catch (NumberFormatException e) {
			throw new LiteralNumberFormatException(
					"Unable to convert sqm literal [" + originalText + "] to BigDecimal",
					e
			);
		}
	}

	@Override
	public Object visitParameterExpression(HqlParser.ParameterExpressionContext ctx) {
		return ctx.parameter().accept( this );
	}

	@Override
	public NamedParameterSqmExpression visitNamedParameter(HqlParser.NamedParameterContext ctx) {
		return new NamedParameterSqmExpression( ctx.identifier().getText() );
	}

	@Override
	public PositionalParameterSqmExpression visitPositionalParameter(HqlParser.PositionalParameterContext ctx) {
		return new PositionalParameterSqmExpression( Integer.valueOf( ctx.INTEGER_LITERAL().getText() ) );
	}

	@Override
	public GenericFunctionSqmExpression visitJpaNonStandardFunction(HqlParser.JpaNonStandardFunctionContext ctx) {
		final String functionName = ctx.nonStandardFunctionName().getText();
		final List<SqmExpression> functionArguments = visitNonStandardFunctionArguments( ctx.nonStandardFunctionArguments() );

		// todo : integrate some form of SqlFunction look-up using the ParsingContext so we can resolve the "type"
		return new GenericFunctionSqmExpression( functionName, null, functionArguments );
	}

	@Override
	public GenericFunctionSqmExpression visitNonStandardFunction(HqlParser.NonStandardFunctionContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			throw new StrictJpaComplianceViolation(
					"Encountered non-compliant non-standard function call [" +
							ctx.nonStandardFunctionName() + "], but strict JPQL compliance was requested; use JPA's FUNCTION(functionName[,...]) syntax name instead",
					StrictJpaComplianceViolation.Type.FUNCTION_CALL
			);
		}

		final String functionName = ctx.nonStandardFunctionName().getText();
		final List<SqmExpression> functionArguments = visitNonStandardFunctionArguments( ctx.nonStandardFunctionArguments() );

		// todo : integrate some form of SqlFunction look-up using the ParsingContext so we can resolve the "type"
		return new GenericFunctionSqmExpression( functionName, null, functionArguments );
	}

	@Override
	public List<SqmExpression> visitNonStandardFunctionArguments(HqlParser.NonStandardFunctionArgumentsContext ctx) {
		final List<SqmExpression> arguments = new ArrayList<SqmExpression>();

		for ( HqlParser.ExpressionContext expressionContext : ctx.expression() ) {
			arguments.add( (SqmExpression) expressionContext.accept( this ) );
		}

		return arguments;
	}

	@Override
	public AggregateSqmFunction visitAggregateFunction(HqlParser.AggregateFunctionContext ctx) {
		return (AggregateSqmFunction) super.visitAggregateFunction( ctx );
	}

	@Override
	public AvgSqmFunction visitAvgFunction(HqlParser.AvgFunctionContext ctx) {
		final SqmExpression expr = (SqmExpression) ctx.expression().accept( this );
		return new AvgSqmFunction(
				expr,
				ctx.DISTINCT() != null,
				(BasicType) expr.getExpressionType()
		);
	}

	@Override
	public CastFunctionSqmExpression visitCastFunction(HqlParser.CastFunctionContext ctx) {
		return new CastFunctionSqmExpression(
				(SqmExpression) ctx.expression().accept( this ),
				parsingContext.getConsumerContext().getDomainMetamodel().resolveCastTargetType( ctx.dataType().IDENTIFIER().getText() )
		);
	}

	@Override
	public ConcatFunctionSqmExpression visitConcatFunction(HqlParser.ConcatFunctionContext ctx) {
		final List<SqmExpression> arguments = new ArrayList<SqmExpression>();
		for ( HqlParser.ExpressionContext argument : ctx.expression() ) {
			arguments.add( (SqmExpression) argument.accept( this ) );
		}

		return new ConcatFunctionSqmExpression( (BasicType) arguments.get( 0 ).getExpressionType(), arguments );
	}

	@Override
	public AggregateSqmFunction visitCountFunction(HqlParser.CountFunctionContext ctx) {
		final BasicType longType = parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Long.class );
		if ( ctx.ASTERISK() != null ) {
			return new CountStarSqmFunction( ctx.DISTINCT() != null, longType );
		}
		else {
			return new CountSqmFunction(
					(SqmExpression) ctx.expression().accept( this ),
					ctx.DISTINCT() != null,
					longType
			);
		}
	}

	@Override
	public MaxSqmFunction visitMaxFunction(HqlParser.MaxFunctionContext ctx) {
		final SqmExpression expr = (SqmExpression) ctx.expression().accept( this );
		return new MaxSqmFunction(
				expr,
				ctx.DISTINCT() != null,
				(BasicType) expr.getExpressionType()
		);
	}

	@Override
	public MinSqmFunction visitMinFunction(HqlParser.MinFunctionContext ctx) {
		final SqmExpression expr = (SqmExpression) ctx.expression().accept( this );
		return new MinSqmFunction(
				expr,
				ctx.DISTINCT() != null,
				(BasicType) expr.getExpressionType()
		);
	}

	@Override
	public SubstringFunctionSqmExpression visitSubstringFunction(HqlParser.SubstringFunctionContext ctx) {
		final SqmExpression source = (SqmExpression) ctx.expression().accept( this );
		final SqmExpression start = (SqmExpression) ctx.substringFunctionStartArgument().accept( this );
		final SqmExpression length = ctx.substringFunctionLengthArgument() == null
				? null
				: (SqmExpression) ctx.substringFunctionLengthArgument().accept( this );
		return new SubstringFunctionSqmExpression( (BasicType) source.getExpressionType(), source, start, length );
	}

	@Override
	public SumSqmFunction visitSumFunction(HqlParser.SumFunctionContext ctx) {
		final SqmExpression expr = (SqmExpression) ctx.expression().accept( this );
		return new SumSqmFunction(
				expr,
				ctx.DISTINCT() != null,
				ExpressionTypeHelper.resolveSingleNumericType(
						(BasicType) expr.getExpressionType(),
						parsingContext.getConsumerContext()
				)
		);
	}

	@Override
	public TrimFunctionSqmExpression visitTrimFunction(HqlParser.TrimFunctionContext ctx) {
		final SqmExpression source = (SqmExpression) ctx.expression().accept( this );
		return new TrimFunctionSqmExpression(
				(BasicType) source.getExpressionType(),
				visitTrimSpecification( ctx.trimSpecification() ),
				visitTrimCharacter( ctx.trimCharacter() ),
				source
		);
	}

	@Override
	public TrimFunctionSqmExpression.Specification visitTrimSpecification(HqlParser.TrimSpecificationContext ctx) {
		if ( ctx.LEADING() != null ) {
			return TrimFunctionSqmExpression.Specification.LEADING;
		}
		else if ( ctx.TRAILING() != null ) {
			return TrimFunctionSqmExpression.Specification.TRAILING;
		}

		// JPA says the default is BOTH
		return TrimFunctionSqmExpression.Specification.BOTH;
	}

	@Override
	public LiteralCharacterSqmExpression visitTrimCharacter(HqlParser.TrimCharacterContext ctx) {
		if ( ctx.CHARACTER_LITERAL() != null ) {
			final String trimCharText = ctx.CHARACTER_LITERAL().getText();
			if ( trimCharText.length() != 1 ) {
				throw new SemanticException( "Expecting [trim character] for TRIM function to be  single character, found : " + trimCharText );
			}
			return new LiteralCharacterSqmExpression( trimCharText.charAt( 0 ), parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Character.class ) );
		}
		if ( ctx.STRING_LITERAL() != null ) {
			final String trimCharText = ctx.STRING_LITERAL().getText();
			if ( trimCharText.length() != 1 ) {
				throw new SemanticException( "Expecting [trim character] for TRIM function to be  single character, found : " + trimCharText );
			}
			return new LiteralCharacterSqmExpression( trimCharText.charAt( 0 ), parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Character.class ) );
		}

		// JPA says space is the default
		return new LiteralCharacterSqmExpression( ' ', parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Character.class ) );
	}

	@Override
	public UpperFunctionSqmExpression visitUpperFunction(HqlParser.UpperFunctionContext ctx) {
		final SqmExpression expression = (SqmExpression) ctx.expression().accept( this );
		return new UpperFunctionSqmExpression(
				(BasicType) expression.getExpressionType(),
				expression
		);
	}

	@Override
	public LowerFunctionSqmExpression visitLowerFunction(HqlParser.LowerFunctionContext ctx) {
		final SqmExpression expression = (SqmExpression) ctx.expression().accept( this );
		return new LowerFunctionSqmExpression(
				(BasicType) expression.getExpressionType(),
				expression
		);
	}

	@Override
	public CollectionSizeSqmFunction visitCollectionSizeFunction(HqlParser.CollectionSizeFunctionContext ctx) {
		final Binding pathResolution = (Binding) ctx.path().accept( this );

		if ( !AttributeBinding.class.isInstance( pathResolution ) ) {
			throw new SemanticException(
					"size() function can only be applied to path expressions which resolve to an attribute; specified " +
							"path [" + ctx.path().getText() + "] resolved to " + pathResolution.getClass().getName()
			);
		}

		final AttributeBinding attributeBinding = (AttributeBinding) pathResolution;
		if ( !PluralAttribute.class.isInstance( attributeBinding.getBoundModelType() ) ) {
			throw new SemanticException(
					"size() function can only be applied to path expressions which resolve to a collection; specified " +
							"path [" + ctx.path().getText() + "] resolved to " + pathResolution
			);
		}

		return new CollectionSizeSqmFunction(
				attributeBinding,
				parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Long.class )
		);
	}

	@Override
	public CollectionIndexSqmFunction visitCollectionIndexFunction(HqlParser.CollectionIndexFunctionContext ctx) {
		final String alias = ctx.identifier().getText();
		final FromElement fromElement = currentQuerySpecProcessingState.getFromElementBuilder().getAliasRegistry().findFromElementByAlias( alias );

		if ( !PluralAttribute.class.isInstance( fromElement.getBoundModelType() ) ) {
			throw new SemanticException(
					"index() function can only be applied to identification variables which resolve to a collection; specified " +
							"identification variable [" + alias + "] resolved to " + fromElement.getBoundModelType()
			);
		}

		final PluralAttribute collectionDescriptor = (PluralAttribute) fromElement.getBoundModelType();
		if ( collectionDescriptor.getCollectionClassification() != PluralAttribute.CollectionClassification.MAP
				&& collectionDescriptor.getCollectionClassification() != PluralAttribute.CollectionClassification.LIST ) {
			throw new SemanticException(
					"index() function can only be applied to identification variables which resolve to an " +
							"indexed collection (map,list); specified identification variable [" + alias +
							"] resolved to " + collectionDescriptor
			);
		}

		return new CollectionIndexSqmFunction( fromElement, collectionDescriptor.getIndexType() );
	}

	@Override
	public MaxElementSqmFunction visitMaxElementFunction(HqlParser.MaxElementFunctionContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			throw new StrictJpaComplianceViolation( StrictJpaComplianceViolation.Type.HQL_COLLECTION_FUNCTION );
		}

		final Binding pathResolution = (Binding) ctx.path().accept( this );

		if ( !PluralAttribute.class.isInstance( pathResolution.getBoundModelType() ) ) {
			throw new SemanticException(
					"maxelement() function can only be applied to path expressions which resolve to a " +
							"collection; specified path [" + ctx.path().getText() +
							"] resolved to " + pathResolution.getBoundModelType()
			);
		}

		final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
		return new MaxElementSqmFunction( pathResolution.getBoundFromElementBinding().getFromElement(), pluralAttribute.getElementType() );
	}

	@Override
	public MinElementSqmFunction visitMinElementFunction(HqlParser.MinElementFunctionContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			throw new StrictJpaComplianceViolation( StrictJpaComplianceViolation.Type.HQL_COLLECTION_FUNCTION );
		}

		final Binding pathResolution = (Binding) ctx.path().accept( this );
		if ( !PluralAttribute.class.isInstance( pathResolution.getBoundModelType() ) ) {
			throw new SemanticException(
					"minelement() function can only be applied to path expressions which resolve to a " +
							"collection; specified path [" + ctx.path().getText() + "] resolved to "
							+ pathResolution.getBoundModelType()
			);
		}

		final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
		return new MinElementSqmFunction( pathResolution.getBoundFromElementBinding().getFromElement(), pluralAttribute.getElementType() );
	}

	@Override
	public MaxIndexSqmFunction visitMaxIndexFunction(HqlParser.MaxIndexFunctionContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			throw new StrictJpaComplianceViolation( StrictJpaComplianceViolation.Type.HQL_COLLECTION_FUNCTION );
		}

		final Binding pathResolution = (Binding) ctx.path().accept( this );
		if ( PluralAttribute.class.isInstance( pathResolution.getBoundModelType() ) ) {
			final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
			if ( pluralAttribute.getCollectionClassification() == PluralAttribute.CollectionClassification.LIST ) {
				return new MaxIndexSqmFunction(
						pathResolution.getBoundFromElementBinding().getFromElement(),
						parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Integer.class )
				);
			}
			else if ( pluralAttribute.getCollectionClassification() == PluralAttribute.CollectionClassification.MAP ) {
				return new MaxIndexSqmFunction(
						pathResolution.getBoundFromElementBinding().getFromElement(),
						pluralAttribute.getIndexType()
				);
			}
		}

		throw new SemanticException(
				"maxindex() function can only be applied to path expressions which resolve to an " +
						"indexed collection (list,map); specified path [" + ctx.path().getText() +
						"] resolved to " + pathResolution.getBoundModelType()
		);
	}

	@Override
	public MinIndexSqmFunction visitMinIndexFunction(HqlParser.MinIndexFunctionContext ctx) {
		if ( parsingContext.getConsumerContext().useStrictJpaCompliance() ) {
			throw new StrictJpaComplianceViolation( StrictJpaComplianceViolation.Type.HQL_COLLECTION_FUNCTION );
		}

		final Binding pathResolution = (Binding) ctx.path().accept( this );
		if ( PluralAttribute.class.isInstance( pathResolution.getBoundModelType() ) ) {
			final PluralAttribute pluralAttribute = (PluralAttribute) pathResolution.getBoundModelType();
			if ( pluralAttribute.getCollectionClassification() == PluralAttribute.CollectionClassification.LIST ) {
				return new MinIndexSqmFunction(
						pathResolution.getBoundFromElementBinding().getFromElement(),
						parsingContext.getConsumerContext().getDomainMetamodel().getBasicType( Integer.class )
				);
			}
			else if ( pluralAttribute.getCollectionClassification() == PluralAttribute.CollectionClassification.MAP ) {
				return new MinIndexSqmFunction(
						pathResolution.getBoundFromElementBinding().getFromElement(),
						pluralAttribute.getIndexType()
				);
			}
		}

		throw new SemanticException(
				"minindex() function can only be applied to path expressions which resolve to an " +
						"indexed collection (list,map); specified path [" + ctx.path().getText() +
						"] resolved to " + pathResolution.getBoundModelType()
		);
	}

	@Override
	public SubQuerySqmExpression visitSubQueryExpression(HqlParser.SubQueryExpressionContext ctx) {
		final QuerySpec querySpec = visitQuerySpec( ctx.querySpec() );
		return new SubQuerySqmExpression( querySpec, determineTypeDescriptor( querySpec.getSelectClause() ) );
	}

	private static Type determineTypeDescriptor(SelectClause selectClause) {
		if ( selectClause.getSelections().size() != 0 ) {
			return null;
		}

		final Selection selection = selectClause.getSelections().get( 0 );
		return selection.getExpression().getExpressionType();
	}
}
