/*******************************************************************************
 * Copyright (c) 2011, 2016 Mentor Graphics and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Vladimir Prus (Mentor Graphics) - initial API and implementation
 *     Teodor Madan (Freescale Semiconductor) - Bug 486521: attaching to selected process
 *******************************************************************************/

package org.eclipse.cdt.dsf.gdb.internal.ui.osview;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.dsf.gdb.service.IGDBHardwareAndOS2.IResourcesInformation;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Image;

/* Table data provider that exposes information about OS resources
 * of specific class. Constructed from MI output. Once constructed,
 * this class is immutable.
 */
class OSData extends LabelProvider implements ITableLabelProvider, IStructuredContentProvider {
	private IResourcesInformation data;
	private boolean[] columnIsInteger;
	private List<Integer> remap;

	public OSData(String resourceClass, IResourcesInformation data) {
		this.data = data;
		determineColumnTypes();

		remap = new ArrayList<>(data.getColumnNames().length);
		for (int i = 0; i < data.getColumnNames().length; ++i)
			remap.add(i);

		if (resourceClass.equals("processes")) //$NON-NLS-1$
			sendToEnd("Command"); //$NON-NLS-1$

		if (resourceClass.equals("threads")) //$NON-NLS-1$
			sendToEnd("Command"); //$NON-NLS-1$

		if (resourceClass.equals("modules")) //$NON-NLS-1$
			sendToEnd("Dependencies"); //$NON-NLS-1$
	}

	// Determine column types, for the purpose of proper sorting
	private void determineColumnTypes() {
		String[] columnNames = data.getColumnNames();
		String[][] content = data.getContent();

		columnIsInteger = new boolean[columnNames.length];

		boolean[] columnHasInteger = new boolean[columnNames.length];
		boolean[] columnHasOther = new boolean[columnNames.length];

		for (int i = 0; i < content.length; ++i) {
			for (int j = 0; j < content[i].length; ++j) {
				if (!columnHasOther[j]) {
					try {
						Integer.parseInt(content[i][j]);
						columnHasInteger[j] = true;
					} catch (NumberFormatException e) {
						columnHasOther[j] = true;
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}

			}
		}

		for (int j = 0; j < data.getColumnNames().length; ++j) {
			columnIsInteger[j] = columnHasInteger[j] && !columnHasOther[j];
		}
	}

	/* Make column named 'column' appear last in UI. */
	private void sendToEnd(String column) {
		// Find index in the remap array (which is equal to index in UI)
		// at which column named 'column' is found.
		int index = -1;
		for (int i = 0; i < remap.size(); ++i)
			if (data.getColumnNames()[remap.get(i)].equals(column)) {
				index = i;
				break;
			}
		if (index == -1)
			return;

		// Move the element to the end of the list
		remap.add(remap.remove(index));
	}

	public int getColumnCount() {
		return remap.size();
	}

	public String getColumnName(int i) {
		return data.getColumnNames()[remap.get(i)];
	}

	public boolean getColumnIsInteger(int j) {
		return columnIsInteger[remap.get(j)];
	}

	@Override
	public String getColumnText(Object obj, int index) {
		return ((IResourcesInformation) obj).getContent()[0][remap.get(index)];
	}

	@Override
	public Image getColumnImage(Object obj, int index) {
		return getImage(obj);
	}

	@Override
	public Image getImage(Object obj) {
		return null;
	}

	@Override
	public void inputChanged(Viewer v, Object oldInput, Object newInput) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object[] getElements(Object parent) {
		// split into array of resource information for each raw
		String[][] content = data.getContent();
		IResourcesInformation[] split_ri = new IResourcesInformation[content.length];
		for (int i = 0; i < content.length; ++i) {
			final String[][] row_content = new String[1][content[i].length];
			row_content[0] = content[i];
			split_ri[i] = new IResourcesInformation() {
				@Override
				public String[] getColumnNames() {
					return data.getColumnNames();
				}

				@Override
				public String[][] getContent() {
					return row_content;
				}
			};
		}
		return split_ri;
	}
}
