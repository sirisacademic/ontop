/***
 * Copyright (c) 2008, Mariano Rodriguez-Muro.
 * All rights reserved.
 *
 * The OBDA-API is licensed under the terms of the Lesser General Public
 * License v.3 (see OBDAAPI_LICENSE.txt for details). The components of this
 * work include:
 * 
 * a) The OBDA-API developed by the author and licensed under the LGPL; and, 
 * b) third-party components licensed under terms that may be different from 
 *   those of the LGPL.  Information about such licenses can be found in the 
 *   file named OBDAAPI_3DPARTY-LICENSES.txt.
 */

package inf.unibz.it.obda.gui.swing.datasource.panels;

import inf.unibz.it.obda.api.datasource.JDBCConnectionManager;
import inf.unibz.it.obda.domain.DataSource;
import inf.unibz.it.obda.gui.swing.datasource.DatasourceSelectorListener;
import inf.unibz.it.obda.gui.swing.utils.OBDAProgessMonitor;
import inf.unibz.it.obda.gui.swing.utils.OBDAProgressListener;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

import javax.swing.JOptionPane;
import javax.swing.table.TableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author  mariano
 */
public class SQLQueryPanel extends javax.swing.JPanel implements 
    DatasourceSelectorListener {
    	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7600557919206933923L;
	String execute_Query;
	
	Logger								log				= LoggerFactory.getLogger(SQLQueryPanel.class);

	private DataSource selectedSource;

    /** Creates new form SQLQueryPanel */
    public SQLQueryPanel(DataSource ds,String execute_Query) {
    	
    	this();
    	this.execute_Query=execute_Query;
    	txtSqlQuery.setText(execute_Query);
    	selectedSource = ds;
    	cmdExecuteActionPerformed(null);    
    }
    
    
    public SQLQueryPanel() {
      initComponents();
      cmdExecute.setMnemonic('x');
    }
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        splSqlQuery = new javax.swing.JSplitPane();
        pnlSqlQuery = new javax.swing.JPanel();
        scrSqlQuery = new javax.swing.JScrollPane();
        txtSqlQuery = new javax.swing.JTextArea();
        cmdExecute = new javax.swing.JButton();
        pnlQueryResult = new javax.swing.JPanel();
        scrQueryResult = new javax.swing.JScrollPane();
        tblQueryResult = new javax.swing.JTable();

        setFont(new java.awt.Font("Arial", 0, 18));
        setLayout(new java.awt.BorderLayout());

        splSqlQuery.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        pnlSqlQuery.setMinimumSize(new java.awt.Dimension(156, 100));
        pnlSqlQuery.setPreferredSize(new java.awt.Dimension(156, 100));
        pnlSqlQuery.setLayout(new java.awt.GridBagLayout());

        txtSqlQuery.setColumns(20);
        txtSqlQuery.setRows(2);
        txtSqlQuery.setBorder(null);
        scrSqlQuery.setViewportView(txtSqlQuery);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 2.0;
        gridBagConstraints.weighty = 2.0;
        pnlSqlQuery.add(scrSqlQuery, gridBagConstraints);

        cmdExecute.setText("Execute");
        cmdExecute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdExecuteActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.RELATIVE;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.RELATIVE;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        pnlSqlQuery.add(cmdExecute, gridBagConstraints);

        splSqlQuery.setLeftComponent(pnlSqlQuery);

        pnlQueryResult.setLayout(new java.awt.BorderLayout());

        tblQueryResult.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Results"
            }
        ));
        tblQueryResult.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        tblQueryResult.setRowHeight(21);
        scrQueryResult.setViewportView(tblQueryResult);

        pnlQueryResult.add(scrQueryResult, java.awt.BorderLayout.CENTER);

        splSqlQuery.setRightComponent(pnlQueryResult);

        add(splSqlQuery, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void cmdExecuteActionPerformed(java.awt.event.ActionEvent evt) {                                               
    	try {
    		if(selectedSource == null){
    			JOptionPane.showMessageDialog(this, "Please select data source first", "Error", JOptionPane.ERROR_MESSAGE);
    		}else{
				OBDAProgessMonitor progMonitor = new OBDAProgessMonitor();
				CountDownLatch latch = new CountDownLatch(1);
				ExecuteSQLQueryAction action = new ExecuteSQLQueryAction(latch);
				progMonitor.addProgressListener(action);
				progMonitor.addProgressListener(action);
				progMonitor.start();
				action.run();
				latch.await();
				progMonitor.stop();
				ResultSet set = action.getResult();
				if(set != null){
					IncrementalResultSetTableModel model = new IncrementalResultSetTableModel(set);
					tblQueryResult.setModel(model);
				}
    		}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Error while executing query.\n Please refer to the log file for more information.");
			log.error("Error while executing query.",e);
		}

    }                                             

    	
  @Override
  public void datasourceChanged(DataSource oldSource, DataSource newSource)
  {
    this.selectedSource = newSource;
  }  

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cmdExecute;
    private javax.swing.JPanel pnlQueryResult;
    private javax.swing.JPanel pnlSqlQuery;
    private javax.swing.JScrollPane scrQueryResult;
    private javax.swing.JScrollPane scrSqlQuery;
    private javax.swing.JSplitPane splSqlQuery;
    private javax.swing.JTable tblQueryResult;
    private javax.swing.JTextArea txtSqlQuery;
    // End of variables declaration//GEN-END:variables
    
    private class ExecuteSQLQueryAction implements OBDAProgressListener{

    	CountDownLatch latch = null;
    	Thread thread = null;
    	ResultSet result = null;
    	Statement statement = null;
    	
    	private ExecuteSQLQueryAction (CountDownLatch latch){
    		this.latch = latch;
    	}
    	
		@Override
		public void actionCanceled() {
			try {
				if(thread != null){
					thread.interrupt();
				}
				if(statement != null && !statement.isClosed()){
					statement.close();
				}
				result = null;
				latch.countDown();
			} catch (SQLException e) {
				latch.countDown();
				JOptionPane.showMessageDialog(null, "Error while canceling action.\n Please refer to the log file for more information.");
				log.error("Error while counting tuples.",e);
			}					
		}
		
		public ResultSet getResult(){
			return result;
		}
		
		public void run(){
			thread = new Thread() {
				public void run() {
					try {
				    	TableModel oldmodel = tblQueryResult.getModel();
				  
						if ((oldmodel != null) && (oldmodel instanceof IncrementalResultSetTableModel)) {
							IncrementalResultSetTableModel rstm = (IncrementalResultSetTableModel) oldmodel;
							rstm.close();
						}
						JDBCConnectionManager man =JDBCConnectionManager.getJDBCConnectionManager();
						man.setProperty(JDBCConnectionManager.JDBC_AUTOCOMMIT, false);
						man.setProperty(JDBCConnectionManager.JDBC_RESULTSETTYPE, ResultSet.TYPE_FORWARD_ONLY);							
						statement = man.getStatement(selectedSource.getSourceID(), selectedSource); //EK
						result = statement.executeQuery(txtSqlQuery.getText());
						latch.countDown();
					} catch (Exception e) {
						latch.countDown();
						JOptionPane.showMessageDialog(null, "Error while executing query.\n Please refer to the log file for more information.");
						log.error("Error while executing query.",e);
					}
				}
			};
			thread.start();
		}
    	
    }
}
