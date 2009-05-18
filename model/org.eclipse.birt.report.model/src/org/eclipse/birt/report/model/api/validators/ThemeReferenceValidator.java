/*******************************************************************************
 * Copyright (c) 2004, 2005, 2006, 2007, 2008, 2009 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.api.validators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.command.ThemeException;
import org.eclipse.birt.report.model.api.util.StringUtil;
import org.eclipse.birt.report.model.core.DesignElement;
import org.eclipse.birt.report.model.core.Module;
import org.eclipse.birt.report.model.validators.AbstractElementValidator;

/**
 * Validates the theme reference in the module.
 * 
 * <h3>Rule</h3> The rule is that the theme referenced in the module should
 * refer to an actual theme.
 * 
 * <h3>Applicability</h3> This validator is only applied to the
 * <code>IModuleModel.THEME_PROP<code> value of <code>Module</code>.
 * 
 */

public class ThemeReferenceValidator extends AbstractElementValidator
{

	/**
	 * Name of this validator.
	 */

	public static final String NAME = "ThemeReferenceValidator"; //$NON-NLS-1$

	private final static ThemeReferenceValidator instance = new ThemeReferenceValidator( );

	/**
	 * Returns the singleton validator instance.
	 * 
	 * @return the validator instance
	 */

	public static ThemeReferenceValidator getInstance( )
	{
		return instance;
	}

	/**
	 * Validates the theme reference value can refer to an actual theme.
	 * 
	 * @param module
	 *            the module
	 * @param element
	 *            the module element holding the theme reference
	 * @return error list, each of which is the instance of
	 *         <code>SemanticException</code>.
	 */

	public List<SemanticException> validate( Module module,
			DesignElement element )
	{
		if ( element != module )
			return Collections.emptyList( );

		List<SemanticException> list = new ArrayList<SemanticException>( );
		String themeName = module.getThemeName( );
		if ( !StringUtil.isEmpty( themeName ) && module.getTheme( module ) == null )
		{
			list.add( new ThemeException( module,
					themeName,
					ThemeException.DESIGN_EXCEPTION_NOT_FOUND ) );
		}
		return list;
	}
}
