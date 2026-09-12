package MUDST_2026_Whatsss.event_registration.auth.domain;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Maps a Java {@code String} onto a PostgreSQL {@code inet} column.
 *
 * <p>PostgreSQL refuses to implicitly cast a bound parameter to {@code inet}, and neither a plain
 * {@code varchar} binding nor {@code Types.OTHER} produces the right type on its own. Binding with
 * an explicit {@code Types.OTHER} object whose SQL type PostgreSQL infers from the target column
 * is what actually works, which is why this small type exists rather than a column annotation.
 *
 * <p>Values are read back as text, since the audit tables only ever display the address.
 */
public class InetType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String first, String second) {
        return Objects.equals(first, second);
    }

    @Override
    public int hashCode(String value) {
        return Objects.hashCode(value);
    }

    @Override
    public String nullSafeGet(ResultSet resultSet,
                              int position,
                              SharedSessionContractImplementor session,
                              Object owner) throws SQLException {
        return resultSet.getString(position);
    }

    @Override
    public void nullSafeSet(PreparedStatement statement,
                            String value,
                            int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        // Types.OTHER lets the driver hand the string to PostgreSQL as an untyped literal, which
        // the server then coerces to inet using the column's own type.
        statement.setObject(index, value, Types.OTHER);
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }
}
