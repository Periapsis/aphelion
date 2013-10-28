/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */

package aphelion.shared.gameconfig;


import aphelion.shared.event.WorkerTask;
import aphelion.shared.gameconfig.LoadYamlTask.Return;
import aphelion.shared.resource.ResourceDB;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.error.YAMLException;

/** Parse a yaml document from a resource file in a worker thread.
 * argument is resource key, return value is a list of yaml documents.
 * @author Joris
 */
public class LoadYamlTask extends WorkerTask<Iterable<String>, List<Return>>
{
        private static final Logger log = Logger.getLogger("aphelion.config");

        private final ResourceDB db;

        public LoadYamlTask(ResourceDB db)
        {
                this.db = db;
        }
        
        @Override
        public List<Return> work(Iterable<String> dbKeys) throws WorkerException
        {
                LinkedList<Return> retList = new LinkedList();
                for (String key : dbKeys)
                {
                        InputStream in = db.getInputStreamSync(key);
                        if (in == null)
                        {
                                log.log(Level.WARNING, "Yaml document with resource key '{0}' not found, skipping", key);
                        }

                        try
                        {
                                Return ret = new Return();
                                ret.fileIdentifier = key;
                                ret.yamlDocuments = GameConfig.loadYaml(in);
                                retList.add(ret);
                        }
                        catch(YAMLException ex)
                        {
                                throw new WorkerException(ex);
                        }
                }
                
                return retList;
        }
        
        public static class Return
        {
                public String fileIdentifier;
                public List<Object> yamlDocuments;
        }
}
