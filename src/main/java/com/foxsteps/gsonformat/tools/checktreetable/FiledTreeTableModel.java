/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.foxsteps.gsonformat.tools.checktreetable;

import com.foxsteps.gsonformat.tools.ux.CellProvider;
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode;
import org.jdesktop.swingx.treetable.DefaultTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableNode;

/**
 * @author vearn
 */
public class FiledTreeTableModel extends DefaultTreeTableModel {

    private String[] _names = {" Key ", "Value", "Data Type", " Field name ","Required", " Field Comment "};
    private Class[] _types = {Object.class, Object.class, Object.class, Object.class, Object.class, Object.class};


    public FiledTreeTableModel(TreeTableNode node) {
        super(node);
    }

    /**
     * 列的类型
     */
    @Override
    public Class getColumnClass(int col) {
        return _types[col];
    }

    /**
     * 列的数量
     */
    @Override
    public int getColumnCount() {
        return _names.length;
    }

    /**
     * 表头显示的内容
     */
    @Override
    public String getColumnName(int column) {
        return _names[column];
    }

    /**
     * 返回在单元格中显示的Object
     */
    @Override
    public Object getValueAt(Object node, int column) {
        Object value = "";
        if (node instanceof DefaultMutableTreeTableNode) {
            DefaultMutableTreeTableNode mutableNode = (DefaultMutableTreeTableNode) node;
            Object o = mutableNode.getUserObject();
            if (o != null && o instanceof CellProvider) {
                CellProvider cellProvider = (CellProvider) o;
                value = cellProvider.getCellTitle(column);

            }
        }
        return value;
    }

    @Override
    public void setValueAt(Object value, Object node, int column) {
        super.setValueAt(value, node, column);
        if (node instanceof DefaultMutableTreeTableNode) {
            DefaultMutableTreeTableNode mutableNode = (DefaultMutableTreeTableNode) node;
            Object o = mutableNode.getUserObject();
            if (o != null && o instanceof CellProvider) {
                CellProvider cellProvider = (CellProvider) o;

                cellProvider.setValueAt(column, value.toString());
            }
        }
    }


    @Override
    public boolean isCellEditable(Object node, int column) {
        if (column == 2) {
            return true;
        }
        if (column == 3) {
            return true;
        }

        if (column == 4) {
            return true;
        }

        if (column == 5) {
            return true;
        }
        return false;
    }
}
