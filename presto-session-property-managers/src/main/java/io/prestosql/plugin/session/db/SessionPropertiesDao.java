/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.session.db;

import com.google.common.annotations.VisibleForTesting;
import io.prestosql.plugin.session.SessionMatchSpec;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;

import java.util.List;

import static io.prestosql.plugin.session.db.util.SessionPropertiesDaoUtil.CLIENT_TAGS_TABLE;
import static io.prestosql.plugin.session.db.util.SessionPropertiesDaoUtil.PROPERTIES_TABLE;
import static io.prestosql.plugin.session.db.util.SessionPropertiesDaoUtil.SESSION_SPECS_TABLE;

/**
 * Dao should guarantee that the list of SessionMatchSpecs is returned in increasing order of priority. i.e. if two
 * rows in the ResultSet specify different values for the same property, the row coming in later will override the
 * value set by the row coming in earlier.
 */
public interface SessionPropertiesDao
{
    @SqlUpdate("CREATE TABLE IF NOT EXISTS " + SESSION_SPECS_TABLE + "(\n" +
            "spec_id BIGINT NOT NULL AUTO_INCREMENT,\n" +
            "user_regex VARCHAR(512),\n" +
            "source_regex VARCHAR(512),\n" +
            "query_type VARCHAR(512),\n" +
            "group_regex VARCHAR(512),\n" +
            "priority INT NOT NULL,\n" +
            "PRIMARY KEY (spec_id)\n" +
            ")")
    void createSessionSpecsTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS " + CLIENT_TAGS_TABLE + "(\n" +
            "tag_spec_id BIGINT NOT NULL,\n" +
            "client_tag VARCHAR(512) NOT NULL,\n" +
            "PRIMARY KEY (tag_spec_id, client_tag),\n" +
            "FOREIGN KEY (tag_spec_id) REFERENCES session_specs (spec_id)\n" +
            ")")
    void createSessionClientTagsTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS " + PROPERTIES_TABLE + "(\n" +
            "property_spec_id BIGINT NOT NULL,\n" +
            "session_property_name VARCHAR(512),\n" +
            "session_property_value VARCHAR(512),\n" +
            "PRIMARY KEY (property_spec_id, session_property_name),\n" +
            "FOREIGN KEY (property_spec_id) REFERENCES session_specs (spec_id)\n" +
            ")")
    void createSessionPropertiesTable();

    @SqlUpdate("DROP TABLE IF EXISTS " + SESSION_SPECS_TABLE)
    void dropSessionSpecsTable();

    @SqlUpdate("DROP TABLE IF EXISTS " + CLIENT_TAGS_TABLE)
    void dropSessionClientTagsTable();

    @SqlUpdate("DROP TABLE IF EXISTS " + PROPERTIES_TABLE)
    void dropSessionPropertiesTable();

    @SqlQuery("SELECT " +
            "S.spec_id,\n" +
            "S.user_regex,\n" +
            "S.source_regex,\n" +
            "S.query_type,\n" +
            "S.group_regex,\n" +
            "S.client_tags,\n" +
            "GROUP_CONCAT(P.session_property_name ORDER BY P.session_property_name) session_property_names,\n" +
            "GROUP_CONCAT(P.session_property_value ORDER BY P.session_property_name) session_property_values\n" +
            "FROM\n" +
            "(SELECT\n" +
            "A.spec_id, A.user_regex, A.source_regex, A.query_type, A.group_regex, A.priority,\n" +
            "GROUP_CONCAT(DISTINCT B.client_tag) client_tags\n" +
            "FROM " + SESSION_SPECS_TABLE + " A\n" +
            "LEFT JOIN " + CLIENT_TAGS_TABLE + " B\n" +
            "ON A.spec_id = B.tag_spec_id\n" +
            "GROUP BY A.spec_id, A.user_regex, A.source_regex, A.query_type, A.group_regex, A.priority)\n" +
            " S JOIN\n" +
            PROPERTIES_TABLE + " P\n" +
            "ON S.spec_id = P.property_spec_id\n" +
            "GROUP BY S.spec_id, S.user_regex, S.source_regex, S.query_type, S.group_regex, S.priority, S.client_tags\n" +
            "ORDER BY S.priority asc")
    @UseRowMapper(SessionMatchSpec.Mapper.class)
    List<SessionMatchSpec> getSessionMatchSpecs();

    @VisibleForTesting
    @SqlUpdate("INSERT INTO " + SESSION_SPECS_TABLE + " (spec_id, user_regex, source_regex, query_type, group_regex, priority)\n" +
            "VALUES (:spec_id, :user_regex, :source_regex, :query_type, :group_regex, :priority)")
    void insertSpecRow(
            @Bind("spec_id") long specId,
            @Bind("user_regex") String userRegex,
            @Bind("source_regex") String sourceRegex,
            @Bind("query_type") String queryType,
            @Bind("group_regex") String groupRegex,
            @Bind("priority") int priority);

    @VisibleForTesting
    @SqlUpdate("INSERT INTO " + CLIENT_TAGS_TABLE + " (tag_spec_id, client_tag) VALUES (:spec_id, :client_tag)")
    void insertClientTag(@Bind("spec_id") long specId, @Bind("client_tag") String clientTag);

    @VisibleForTesting
    @SqlUpdate("INSERT INTO " + PROPERTIES_TABLE + " (property_spec_id, session_property_name, session_property_value)\n" +
            "VALUES (:property_spec_id, :session_property_name, :session_property_value)")
    void insertSessionProperty(
            @Bind("property_spec_id") long propertySpecId,
            @Bind("session_property_name") String sessionPropertyName,
            @Bind("session_property_value") String sessionPropertyValue);
}
