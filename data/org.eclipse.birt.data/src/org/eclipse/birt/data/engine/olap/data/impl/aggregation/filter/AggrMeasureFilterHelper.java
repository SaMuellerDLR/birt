/*******************************************************************************
 * Copyright (c) 2004, 2007 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.data.engine.olap.data.impl.aggregation.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.birt.data.engine.core.DataException;
import org.eclipse.birt.data.engine.olap.data.api.CubeQueryExecutorHelper;
import org.eclipse.birt.data.engine.olap.data.api.DimLevel;
import org.eclipse.birt.data.engine.olap.data.api.IAggregationResultSet;
import org.eclipse.birt.data.engine.olap.data.api.ILevel;
import org.eclipse.birt.data.engine.olap.data.api.cube.ICube;
import org.eclipse.birt.data.engine.olap.data.api.cube.IDimension;
import org.eclipse.birt.data.engine.olap.data.impl.AggregationDefinition;
import org.eclipse.birt.data.engine.olap.data.impl.AggregationFunctionDefinition;
import org.eclipse.birt.data.engine.olap.data.impl.dimension.Dimension;
import org.eclipse.birt.data.engine.olap.data.impl.dimension.Level;
import org.eclipse.birt.data.engine.olap.data.impl.dimension.Member;
import org.eclipse.birt.data.engine.olap.data.util.BufferedPrimitiveDiskArray;
import org.eclipse.birt.data.engine.olap.data.util.IDiskArray;
import org.eclipse.birt.data.engine.olap.data.util.SetUtil;
import org.eclipse.birt.data.engine.olap.util.OlapExpressionCompiler;
import org.eclipse.birt.data.engine.olap.util.filter.CubePosFilter;
import org.eclipse.birt.data.engine.olap.util.filter.IAggrMeasureFilterEvalHelper;
import org.eclipse.birt.data.engine.olap.util.filter.InvalidCubePosFilter;
import org.eclipse.birt.data.engine.olap.util.filter.ValidCubePosFilter;

/**
 * 
 */

public class AggrMeasureFilterHelper
{

	private Map dimMap = null;
	private IAggregationResultSet[] resultSet;

	/**
	 * 
	 * @param cube
	 * @param resultSet
	 */
	public AggrMeasureFilterHelper( ICube cube,
			IAggregationResultSet[] resultSet )
	{
		dimMap = new HashMap( );
		IDimension[] dimension = cube.getDimesions( );
		for ( int i = 0; i < dimension.length; i++ )
		{
			dimMap.put( dimension[i].getName( ), dimension[i] );
		}
		this.resultSet = resultSet;
	}

	/**
	 * 
	 * @param resultSet
	 * @throws DataException
	 * @throws IOException
	 */
	public List getCubePosFilters( List jsMeasureEvalFilterHelper )
			throws DataException, IOException
	{
		String[] aggregationNames = populateAggregationNames( jsMeasureEvalFilterHelper );

		List cubePosFilterList = new ArrayList( );
		for ( int i = 0; i < resultSet.length; i++ )
		{
			if ( hasDefinition( resultSet[i], aggregationNames ) )
			{
				Map levelMap = populateLevelMap( resultSet[i] );
				final int dimSize = levelMap.size( );
				List[] levelListArray = new List[dimSize];
				levelMap.values( ).toArray( levelListArray );
				String[] dimensionNames = new String[dimSize];
				levelMap.keySet( ).toArray( dimensionNames );

				IDiskArray rowIndexArray = collectValidRowIndexArray( resultSet[i],
						jsMeasureEvalFilterHelper,
						aggregationNames );
				CubePosFilter cubePosFilter = null;;
				if ( rowIndexArray.size( ) <= resultSet[i].length( ) / 2 )
				{// use valid position filter
					cubePosFilter = getValidPosFilter( resultSet[i],
							rowIndexArray,
							dimensionNames,
							levelListArray );
				}
				else
				{// use invalid position filter
					cubePosFilter = getInvalidPosFilter( resultSet[i],
							rowIndexArray,
							dimensionNames,
							levelListArray );
				}
				cubePosFilterList.add( cubePosFilter );
			}
		}
		return cubePosFilterList;
	}

	/**
	 * to indicate whether the specified <code>resultSet</code> has
	 * aggregation definition for any one of the <code>aggregationNames</code>.
	 * 
	 * @param resultSet
	 * @param aggregationNames
	 * @return
	 * @throws IOException
	 */
	private boolean hasDefinition( IAggregationResultSet resultSet,
			String[] aggregationNames ) throws IOException
	{
		for ( int j = 0; j < aggregationNames.length; j++ )
		{
			if ( resultSet.getAggregationIndex( aggregationNames[j] ) >= 0 )
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * @param jsMeasureEvalFilterHelper
	 * @return
	 */
	private String[] populateAggregationNames( List jsMeasureEvalFilterHelper )
	{
		String[] aggregationNames = new String[jsMeasureEvalFilterHelper.size( )];
		for ( int i = 0; i < aggregationNames.length; i++ )
		{
			IAggrMeasureFilterEvalHelper filterHelper = (IAggrMeasureFilterEvalHelper) jsMeasureEvalFilterHelper.get( i );
			aggregationNames[i] = OlapExpressionCompiler.getReferencedScriptObject( filterHelper.getExpression( ),
					"data" );//$NON-NLS-1$
		}
		return aggregationNames;
	}

	/**
	 * 
	 * @param resultSet
	 * @param rowIndexArray
	 * @param dimensionNames
	 * @param levelListArray
	 * @return
	 * @throws IOException
	 * @throws DataException
	 */
	private CubePosFilter getInvalidPosFilter( IAggregationResultSet resultSet,
			IDiskArray rowIndexArray, String[] dimensionNames,
			List[] levelListArray ) throws IOException, DataException
	{
		CubePosFilter cubePosFilter = new InvalidCubePosFilter( dimensionNames );
		int rowIndex = 0;
		for ( int i = 0; i < resultSet.length( ); i++ )
		{
			if ( rowIndex < rowIndexArray.size( ) )
			{
				Integer index = (Integer) rowIndexArray.get( rowIndex );
				if ( i == index.intValue( ) )
				{// ignore the valid positions
					rowIndex++;
					continue;
				}
			}
			resultSet.seek( i );

			IDiskArray[] dimPositions = new IDiskArray[dimensionNames.length];
			for ( int j = 0; j < levelListArray.length; j++ )
			{
				for ( int k = 0; k < levelListArray[j].size( ); k++ )
				{
					DimLevel level = (DimLevel) levelListArray[j].get( k );
					int levelIndex = resultSet.getLevelIndex( level );
					Object[] value = resultSet.getLevelKeyValue( levelIndex );
					IDiskArray positions = find( dimensionNames[j],
							level,
							value );
					if ( dimPositions[j] == null )
					{
						dimPositions[j] = positions;
					}
					else
					{
						dimPositions[j] = SetUtil.getIntersection( dimPositions[j],
								positions );
					}
				}
			}
			cubePosFilter.addDimPositions( dimPositions );
			for ( int n = 0; n < dimPositions.length; n++ )
			{
				dimPositions[n].close( );
			}
		}
		return cubePosFilter;
	}

	/**
	 * 
	 * @param resultSet
	 * @param rowIndexArray
	 * @param dimensionNames
	 * @param levelListArray
	 * @return
	 * @throws IOException
	 * @throws DataException
	 */
	private CubePosFilter getValidPosFilter( IAggregationResultSet resultSet,
			IDiskArray rowIndexArray, String[] dimensionNames,
			List[] levelListArray ) throws IOException, DataException
	{
		CubePosFilter cubePosFilter = new ValidCubePosFilter( dimensionNames );
		for ( int i = 0; i < rowIndexArray.size( ); i++ )
		{
			Integer rowIndex = (Integer) rowIndexArray.get( i );
			resultSet.seek( rowIndex.intValue( ) );
			IDiskArray[] dimPositions = new IDiskArray[dimensionNames.length];
			for ( int j = 0; j < levelListArray.length; j++ )
			{
				for ( int k = 0; k < levelListArray[j].size( ); k++ )
				{
					DimLevel level = (DimLevel) levelListArray[j].get( k );
					int levelIndex = resultSet.getLevelIndex( level );
					Object[] value = resultSet.getLevelKeyValue( levelIndex );
					IDiskArray positions = find( dimensionNames[j],
							level,
							value );
					if ( dimPositions[j] == null )
					{
						dimPositions[j] = positions;
					}
					else
					{
						dimPositions[j] = SetUtil.getIntersection( dimPositions[j],
								positions );
					}
				}
			}
			cubePosFilter.addDimPositions( dimPositions );
			for ( int n = 0; n < dimPositions.length; n++ )
			{
				dimPositions[n].close( );
			}
		}
		return cubePosFilter;
	}

	/**
	 * collect the filtered result
	 * 
	 * @param resultSet
	 * @param filterHelpers
	 * @param aggregationNames
	 * @return
	 * @throws DataException
	 * @throws IOException
	 */
	private IDiskArray collectValidRowIndexArray(
			IAggregationResultSet resultSet, List filterHelpers,
			String[] aggregationNames ) throws DataException, IOException
	{
		IDiskArray result = new BufferedPrimitiveDiskArray( );
		String[] fieldNames = getFieldNames( resultSet );
		String[] aggrNames = getAggrNames( resultSet.getAggregationDefinition( ) );
		for ( int i = 0; i < resultSet.length( ); i++ )
		{
			resultSet.seek( i );
			RowForFilter row4filter = getRowForFilter( resultSet,
					fieldNames,
					aggrNames );
			boolean isFilterByAll = true;
			for ( int j = 0; j < filterHelpers.size( ); j++ )
			{
				if ( resultSet.getAggregationIndex( aggregationNames[j] ) >= 0 )
				{
					IAggrMeasureFilterEvalHelper filterHelper = (IAggrMeasureFilterEvalHelper) filterHelpers.get( j );
					if ( !filterHelper.evaluateFilter( row4filter ) )
					{
						isFilterByAll = false;
						break;
					}
				}
			}
			if ( isFilterByAll )
			{
				result.add( new Integer( i ) );
			}
		}
		return result;
	}

	/**
	 * 
	 * @param resultSet
	 * @return
	 */
	private Map populateLevelMap( IAggregationResultSet resultSet )
	{
		final DimLevel[] dimLevels = resultSet.getAllLevels( );
		Map levelMap = new HashMap( );
		for ( int j = 0; j < dimLevels.length; j++ )
		{
			final String dimensionName = dimLevels[j].getDimensionName( );
			List list = (List) levelMap.get( dimensionName );
			if ( list == null )
			{
				list = new ArrayList( );
				levelMap.put( dimensionName, list );
			}
			list.add( dimLevels[j] );
		}
		return levelMap;
	}

	/**
	 * 
	 * @param dimensionName
	 * @param level
	 * @param keyValue
	 * @return
	 * @throws DataException
	 * @throws IOException
	 */
	private IDiskArray find( String dimensionName, DimLevel level,
			Object[] keyValue ) throws DataException, IOException
	{
		Dimension dimension = (Dimension) dimMap.get( dimensionName );
		ILevel[] levels = dimension.getHierarchy( ).getLevels( );
		int i = 0;
		for ( ; i < levels.length; i++ )
		{
			if ( level.getLevelName( ).equals( levels[i].getName( ) ) )
			{
				break;
			}
		}
		if ( i < levels.length )
		{
			return dimension.findPosition( (Level) levels[i], keyValue );
		}
		throw new DataException( "Can't find level {0} in the dimension!", level );//$NON-NLS-1$
	}

	/**
	 * 
	 * @param resultSet
	 * @param fieldNames
	 * @param aggrNames
	 * @return
	 * @throws IOException
	 */
	private RowForFilter getRowForFilter( IAggregationResultSet resultSet,
			String[] fieldNames, String[] aggrNames ) throws IOException
	{
		RowForFilter row4filter = new RowForFilter( fieldNames, aggrNames );
		List fieldValues = new ArrayList( );
		Member[] members = resultSet.getCurrentRow( ).getLevelMembers( );
		if ( members != null )
		{
			for ( int i = 0; i < members.length; i++ )
			{
				Object[] keyValues = members[i].getKeyValues( );
				if ( keyValues != null )
				{
					for ( int j = 0; j < keyValues.length; j++ )
					{
						fieldValues.add( keyValues[j] );
					}
				}
				Object[] attrValues = members[i].getAttributes( );
				if ( attrValues != null )
				{
					for ( int j = 0; j < attrValues.length; j++ )
					{
						fieldValues.add( attrValues[j] );
					}
				}
			}
		}
		row4filter.setFieldValues( fieldValues.toArray( ) );
		List aggrValues = new ArrayList( );
		for ( int i = 0; i < aggrNames.length; i++ )
		{
			int index = resultSet.getAggregationIndex( aggrNames[i] );
			aggrValues.add( resultSet.getAggregationValue( index ) );
		}
		row4filter.setAggrValues( aggrValues.toArray( ) );
		return row4filter;
	}

	/**
	 * 
	 * @param aggrDefinition
	 * @return
	 */
	private String[] getAggrNames( AggregationDefinition aggrDefinition )
	{
		final AggregationFunctionDefinition[] aggrFunctions = aggrDefinition.getAggregationFunctions( );
		String[] aggrNames = new String[aggrFunctions.length];
		for ( int i = 0; i < aggrNames.length; i++ )
		{
			aggrNames[i] = aggrFunctions[i].getName( );
		}
		return aggrNames;
	}

	/**
	 * 
	 * @param resultSet
	 * @return
	 */
	private String[] getFieldNames( IAggregationResultSet resultSet )
	{
		List fieldNameList = new ArrayList( );
		DimLevel[] levels = resultSet.getAllLevels( );
		String[][] keyNames = resultSet.getKeyNames( );
		String[][] attrNames = resultSet.getAggributeNames( );
		for ( int i = 0; i < levels.length; i++ )
		{
			int index = resultSet.getLevelIndex( levels[i] );
			if ( keyNames[index] != null )
			{
				for ( int j = 0; j < keyNames[i].length; j++ )
				{
					fieldNameList.add( CubeQueryExecutorHelper.getAttrReference( levels[i].getDimensionName( ),
							levels[i].getLevelName( ),
							keyNames[index][j] ) );
				}
			}
			if ( attrNames[index] != null )
			{
				for ( int j = 0; j < attrNames[i].length; j++ )
				{
					fieldNameList.add( CubeQueryExecutorHelper.getAttrReference( levels[i].getDimensionName( ),
							levels[i].getLevelName( ),
							attrNames[index][j] ) );
				}
			}
		}
		String[] fieldNames = new String[fieldNameList.size( )];
		fieldNameList.toArray( fieldNames );
		return fieldNames;
	}
}
