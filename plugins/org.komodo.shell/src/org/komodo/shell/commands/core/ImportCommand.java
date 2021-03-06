/*
 * JBoss, Home of Professional Open Source.
*
* See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
*
* See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
*/
package org.komodo.shell.commands.core;

import java.io.File;
import java.util.List;
import org.komodo.importer.ImportMessages;
import org.komodo.importer.ImportOptions;
import org.komodo.importer.ImportOptions.ImportType;
import org.komodo.importer.ImportOptions.OptionKeys;
import org.komodo.importer.ddl.DdlImporter;
import org.komodo.importer.vdb.VdbImporter;
import org.komodo.shell.BuiltInShellCommand;
import org.komodo.shell.CompletionConstants;
import org.komodo.shell.Messages;
import org.komodo.shell.api.InvalidCommandArgumentException;
import org.komodo.shell.api.WorkspaceContext;
import org.komodo.shell.api.WorkspaceStatus;
import org.komodo.spi.constants.StringConstants;
import org.komodo.spi.repository.Repository;

/**
 * Import Command 
 */
public class ImportCommand extends BuiltInShellCommand {

    private static final String SUBCMD_DDL = "DDL"; //$NON-NLS-1$

    private static final String SUBCMD_VDB = "VDB"; //$NON-NLS-1$

    /**
     * Constructor.
     * @param name the command name
     * @param wsStatus the workspace status
     */
    public ImportCommand(String name, WorkspaceStatus wsStatus) {
        super(name, wsStatus);
    }

    /**
     * @see org.komodo.shell.api.ShellCommand#execute()
     */
    @Override
    public boolean execute() throws Exception {
        boolean success = false;
        String subcmdArg = requiredArgument(0, Messages.getString("ImportCommand.InvalidArgMsg_SubCommand")); //$NON-NLS-1$

        if (SUBCMD_DDL.equalsIgnoreCase(subcmdArg)) {
            // Check required args
            String fileNameArg = requiredArgument(1, Messages.getString("ImportCommand.InvalidArgMsg_FileName")); //$NON-NLS-1$
            String modelNameArg = requiredArgument(2, Messages.getString("ImportCommand.InvalidArgMsg_ModelName")); //$NON-NLS-1$

            ImportMessages messages = importModel(fileNameArg, modelNameArg);
            if (!messages.hasError()) {
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("ImportCommand.ModelImportSuccessMsg", modelNameArg, fileNameArg)); //$NON-NLS-1$
                if (getWorkspaceStatus().getRecordingStatus())
                    recordCommand(getArguments());
            } else {
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("ImportCommand.ImportFailedMsg", fileNameArg)); //$NON-NLS-1$
                print(CompletionConstants.MESSAGE_INDENT, messages.errorMessagesToString());
            }
        } else if (SUBCMD_VDB.equalsIgnoreCase(subcmdArg)) {
            // Check required args
            String fileNameArg = requiredArgument(1, Messages.getString("ImportCommand.InvalidArgMsg_FileName")); //$NON-NLS-1$

            ImportMessages messages = importVdb(fileNameArg);
            if (!messages.hasError()) {
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("ImportCommand.VdbImportSuccessMsg", fileNameArg)); //$NON-NLS-1$
                if (getWorkspaceStatus().getRecordingStatus())
                    recordCommand(getArguments());
                success = true;
            } else {
                print(CompletionConstants.MESSAGE_INDENT, Messages.getString("ImportCommand.ImportFailedMsg", fileNameArg)); //$NON-NLS-1$
                print(CompletionConstants.MESSAGE_INDENT, messages.errorMessagesToString());
            }
        } else {
            throw new InvalidCommandArgumentException(0, Messages.getString("ImportCommand.InvalidSubCommand")); //$NON-NLS-1$
        }

        return success;
    }

    /**
     * Import model from file and add it to the current project
     * @param modelFile the file containing the model DDL
     * @param modelName the name of the model
     * @return the messages from the import
     * @throws Exception if error occurs
     */
    private ImportMessages importModel(String modelFile, String modelName) throws Exception {
        WorkspaceStatus wsStatus = getWorkspaceStatus();
        ImportMessages importMessages = new ImportMessages();

        WorkspaceContext currentContext = wsStatus.getCurrentContext();
        Repository repository = null;
        try {
            repository = currentContext.getRepository();
        } catch (Exception ex) {
            importMessages.addErrorMessage(ex.getLocalizedMessage());
            return importMessages;
        }

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportType(ImportType.MODEL);
        importOptions.setOption(OptionKeys.NAME, modelName);

        File ddlFile = new File(modelFile);

        DdlImporter importer = new DdlImporter(repository);
        importer.importDdl(ddlFile, importOptions, importMessages);

        return importMessages;
    }

    /**
     * Import vdb from file and add it to the current project
     * @param vdbFile the file containing the vdb xml
     * @return the messages from the import
     * @throws Exception if error occurs
     */
    private ImportMessages importVdb(String vdbFile) throws Exception {
        WorkspaceStatus wsStatus = getWorkspaceStatus();
        ImportMessages importMessages = new ImportMessages();

        WorkspaceContext currentContext = wsStatus.getCurrentContext();
        Repository repository = null;
        try {
            repository = currentContext.getRepository();
        } catch (Exception ex) {
            importMessages.addErrorMessage(ex.getLocalizedMessage());
            return importMessages;
        }

        File xmlFile = new File(vdbFile);

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportType(ImportType.VDB);
        importOptions.setOption(OptionKeys.NAME, xmlFile.getName());

        VdbImporter importer = new VdbImporter(repository);
        importer.importVdb(xmlFile, importOptions, importMessages);

        return importMessages;
    }

    /**
     * @see org.komodo.shell.api.AbstractShellCommand#tabCompletion(java.lang.String, java.util.List)
     */
    @Override
    public int tabCompletion(String lastArgument, List<CharSequence> candidates) {

        if (getArguments().isEmpty()) {
            if (lastArgument == null) {
                candidates.add(SUBCMD_DDL + StringConstants.SPACE);
                candidates.add(SUBCMD_VDB + StringConstants.SPACE);
                return 0;
            }
        }
        return -1;
    }

}
