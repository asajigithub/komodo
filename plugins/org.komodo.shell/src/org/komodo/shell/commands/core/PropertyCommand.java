/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.komodo.shell.commands.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.komodo.shell.BuiltInShellCommand;
import org.komodo.shell.CompletionConstants;
import org.komodo.shell.Messages;
import org.komodo.shell.api.InvalidCommandArgumentException;
import org.komodo.shell.api.WorkspaceContext;
import org.komodo.shell.api.WorkspaceStatus;

/**
 *
 */
public class PropertyCommand extends BuiltInShellCommand {

    private static final String SUBCMD_SET = "SET"; //$NON-NLS-1$
    private static final String SUBCMD_UNSET = "UNSET"; //$NON-NLS-1$
    private static final String SUBCMD_SHOW = "LIST"; //$NON-NLS-1$
    private static final String ALL = "ALL"; //$NON-NLS-1$

    /**
     * Constructor.
     * @param name the command name
     * @param wsStatus the workspace status
     */
    public PropertyCommand(String name, WorkspaceStatus wsStatus) {
        super(name, wsStatus);
    }

    /**
     * @see org.komodo.shell.api.ShellCommand#execute()
     */
    @Override
    public boolean execute() throws Exception {
        String subcmdArg = requiredArgument(0, Messages.getString("PropertyCommand.InvalidArgMsg_SubCommand")); //$NON-NLS-1$

        try {
            if (SUBCMD_SHOW.equalsIgnoreCase(subcmdArg)) {
                WorkspaceContext context = getWorkspaceStatus().getCurrentContext();
                List<String> currentProps = context.getProperties();
                String cType = context.getType().toString();
                String name = context.getName();

                String propListHeader = Messages.getString("PropertyCommand.PropertyListHeader", cType, name); //$NON-NLS-1$
                print(CompletionConstants.MESSAGE_INDENT, propListHeader);
                print(CompletionConstants.MESSAGE_INDENT, String.format("%-25s%-25s", "---------------", "---------------")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                List<String> sortedPropNames = new ArrayList<String>(currentProps);
                Collections.sort(sortedPropNames);
                
                String propNameArg = optionalArgument(1, ALL);
                for (String pName : sortedPropNames) {
                    if (propNameArg.equalsIgnoreCase(ALL) || propNameArg.equalsIgnoreCase(pName)) {
                        print(CompletionConstants.MESSAGE_INDENT, String.format("%-25s%-25s", pName, context.getPropertyValue(pName))); //$NON-NLS-1$
                    }
                }
                if (getWorkspaceStatus().getRecordingStatus())
                    recordCommand(getArguments());  
            } else if (SUBCMD_SET.equalsIgnoreCase(subcmdArg)) {
                String propNameArg = requiredArgument(1, Messages.getString("PropertyCommand.InvalidArgMsg_PropertyName")); //$NON-NLS-1$
                String propValueArg = requiredArgument(2, Messages.getString("PropertyCommand.InvalidArgMsg_PropertyValue")); //$NON-NLS-1$
                setProperty(propNameArg, propValueArg);
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("PropertyCommand.PropertySet", propNameArg)); //$NON-NLS-1$
                if (getWorkspaceStatus().getRecordingStatus())
                    recordCommand(getArguments());
            } else if (SUBCMD_UNSET.equalsIgnoreCase(subcmdArg)) {
                String propNameArg = requiredArgument(1, Messages.getString("PropertyCommand.InvalidArgMsg_PropertyName")); //$NON-NLS-1$
                unsetProperty(propNameArg);
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("PropertyCommand.PropertyUnset", propNameArg)); //$NON-NLS-1$
                if (getWorkspaceStatus().getRecordingStatus())
                    recordCommand(getArguments());
            } else {
                throw new InvalidCommandArgumentException(0, Messages.getString("PropertyCommand.InvalidSubCommand")); //$NON-NLS-1$
            }
        } catch (InvalidCommandArgumentException e) {
            throw e;
        } catch (Exception e) {
            print(CompletionConstants.MESSAGE_INDENT, Messages.getString("PropertyCommand.Failure")); //$NON-NLS-1$
            print(CompletionConstants.MESSAGE_INDENT, "\t" + e.getMessage()); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    /**
     * Sets a property on the artifact.
     * @param artifact
     * @param propName
     * @param propValue
     * @throws Exception 
     */
    private void setProperty(String propName, String propValue) throws Exception {
        WorkspaceStatus wsStatus = getWorkspaceStatus();
        WorkspaceContext context = wsStatus.getCurrentContext();

        context.setPropertyValue(propName, propValue);
    }

    /**
     * Unsets a property on the artifact.
     * @param artifact
     * @param propName
     * @throws Exception 
     */
    private void unsetProperty(String propName) throws Exception {
        setProperty(propName, null);
    }

    /**
     * @see org.komodo.shell.api.AbstractShellCommand#tabCompletion(java.lang.String, java.util.List)
     */
    @Override
    public int tabCompletion(String lastArgument, List<CharSequence> candidates) {
        //
        //		if (getArguments().isEmpty()) {
        //			if (lastArgument == null) {
        //				candidates.add(SUBCMD_SET+StringConstants.SPACE); 
        //				candidates.add(SUBCMD_SHOW+StringConstants.SPACE); 
        //				candidates.add(SUBCMD_UNSET+StringConstants.SPACE);
        //				return 0;
        //			} else if (SUBCMD_SHOW.startsWith(lastArgument.toUpperCase())) { 
        //				candidates.add(SUBCMD_SHOW+StringConstants.SPACE); 
        //				return 0;
        //			} else if (SUBCMD_SET.startsWith(lastArgument.toUpperCase())) { 
        //				candidates.add(SUBCMD_SET+StringConstants.SPACE); 
        //				return 0;
        //			} else if (SUBCMD_UNSET.startsWith(lastArgument.toUpperCase())) {
        //				candidates.add(SUBCMD_UNSET+StringConstants.SPACE); 
        //				return 0;
        //			}
        //		} else if (getArguments().size() == 1) {
        //			String cmdArgUpper = getArguments().get(0).toUpperCase();
        //			if(SUBCMD_SET.equals(cmdArgUpper) || SUBCMD_UNSET.equals(cmdArgUpper) || SUBCMD_SHOW.equals(cmdArgUpper)) {
        //				Map<String,String> propNameValueMap = getWorkspaceStatus().getCurrentContext().getPropertyNameValueMap();
        //				List<String> allCandidates = new ArrayList<String>();
        //				String candidatePostfix = StringConstants.SPACE; 
        //				for(String str:propNameValueMap.keySet()) {
        //					allCandidates.add(str+candidatePostfix);
        //				}
        //				if(SUBCMD_SHOW.equals(cmdArgUpper)) {
        //					allCandidates.add(0,ALL);
        //				}
        //				if(lastArgument==null) {
        //					candidates.addAll(allCandidates);
        //				} else {
        //					for (String prop : allCandidates) {
        //						if (prop.toUpperCase().startsWith(lastArgument.toUpperCase())) {
        //							candidates.add(prop);
        //						}
        //					}
        //				}
        //				return 0;
        //			}
        //		} 
        return -1;
    }

}
