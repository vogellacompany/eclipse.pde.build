/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.builders;

import java.io.*;
import java.net.*;

import org.eclipse.pde.internal.core.ischema.ISchemaDescriptor;

public interface ISchemaTransformer {
	public void transform(ISchemaDescriptor desc, InputStream schema, PrintWriter output, SchemaHandler reporter);
	
	public void transform(ISchemaDescriptor desc, InputStream schema, PrintWriter output, SchemaHandler reporter, URL cssURL);
}
