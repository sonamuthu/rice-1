package org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.resolver;

import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.Node;
import japa.parser.ast.body.FieldDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.QualifiedNameExpr;
import japa.parser.ast.expr.SingleMemberAnnotationExpr;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ojb.broker.metadata.ClassDescriptor;
import org.apache.ojb.broker.metadata.DescriptorRepository;
import org.apache.ojb.broker.metadata.FieldDescriptor;
import org.kuali.rice.devtools.jpa.eclipselink.conv.ojb.OjbUtil;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.ParserUtil;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.AnnotationResolver;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.Level;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.NodeData;

import java.lang.reflect.Field;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;

public class TemporalResolver implements AnnotationResolver {
    private static final Log LOG = LogFactory.getLog(TemporalResolver.class);

    public static final String PACKAGE = "javax.persistence";
    public static final String SIMPLE_NAME = "Temporal";
    public static final String DATE = "DATE";
    public static final String TIMESTAMP = "TIMESTAMP";
    public static final String TIME = "TIME";

    private final Collection<DescriptorRepository> descriptorRepositories;

    public TemporalResolver(Collection<DescriptorRepository> descriptorRepositories) {
        this.descriptorRepositories = descriptorRepositories;
    }

    @Override
    public String getFullyQualifiedName() {
        return PACKAGE + "." + SIMPLE_NAME;
    }

    @Override
    public Level getLevel() {
        return Level.FIELD;
    }

    @Override
    public NodeData resolve(Node node, Object arg) {
        if (!(node instanceof FieldDeclaration)) {
            throw new IllegalArgumentException("this annotation belongs only on FieldDeclaration");
        }

        final FieldDeclaration field = (FieldDeclaration) node;

        if (canBeAnnotated(field)) {
            final TypeDeclaration dclr = (TypeDeclaration) node.getParentNode();
            if (!(dclr.getParentNode() instanceof CompilationUnit)) {
                //handling nested classes
                return null;
            }
            final String name = dclr.getName();
            final String pckg = ((CompilationUnit) dclr.getParentNode()).getPackage().getName().toString();
            final String fullyQualifiedClass = pckg + "." + name;
            final boolean mappedColumn = isMappedColumn(fullyQualifiedClass, ParserUtil.getFieldName(field));
            if (mappedColumn) {
                return getAnnotationNodes(fullyQualifiedClass, ParserUtil.getFieldName(field));
            }
        }
        return null;
    }

    private boolean canBeAnnotated(FieldDeclaration node) {
        return !ModifierSet.isStatic(node.getModifiers());
    }

    private boolean isMappedColumn(String clazz, String fieldName) {
        final ClassDescriptor cd = OjbUtil.findClassDescriptor(clazz, descriptorRepositories);
        if (cd != null) {
            return cd.getFieldDescriptorByName(fieldName) != null;
        }
        return false;
    }

    private NodeData getAnnotationNodes(String clazz, String fieldName) {
        final ClassDescriptor cd = OjbUtil.findClassDescriptor(clazz, descriptorRepositories);
        if (cd != null) {
            final FieldDescriptor fd = cd.getFieldDescriptorByName(fieldName);
            final Class<?> fc = getType(clazz, fieldName);
            final String columnType = fd.getColumnType();
            if (isJavaSqlDate(fc)) {
                LOG.warn(clazz + "." + fieldName + " is a java.sql.Date. " + getWarnMessageFragment(columnType));
            } else if (isJavaSqlTimestamp(fc)) {
                LOG.warn(clazz + "." + fieldName + " is a java.sql.Timestamp. " + getWarnMessageFragment(columnType));
            } else if (isJavaSqlTime(fc)) {
                LOG.warn(clazz + "." + fieldName + " is a java.sql.Time. " + getWarnMessageFragment(columnType));
            } else if (isJavaUtilDate(fc) || isJavaUtilCalendar(fc)) {
                if (DATE.equals(columnType)) {
                    return new NodeData(new SingleMemberAnnotationExpr(new NameExpr(SIMPLE_NAME), new NameExpr("TemporalType.DATE")),
                            new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), SIMPLE_NAME), false, false),
                            Collections.singletonList(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), "TemporalType"), false, false)));
                } else if (TIMESTAMP.equals(columnType)) {
                    return new NodeData(new SingleMemberAnnotationExpr(new NameExpr(SIMPLE_NAME), new NameExpr("TemporalType.TIMESTAMP")),
                            new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), SIMPLE_NAME), false, false),
                            Collections.singletonList(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), "TemporalType"), false, false)));
                } else if (TIME.equals(columnType)) {
                    return new NodeData(new SingleMemberAnnotationExpr(new NameExpr(SIMPLE_NAME), new NameExpr("TemporalType.TIME")),
                            new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), SIMPLE_NAME), false, false),
                            Collections.singletonList(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), "TemporalType"), false, false)));
                }

                LOG.error(clazz + "." + fieldName + " is a java.sql.Date or java.util.Calendar but the column type " + columnType + " is unknown.  Unable to add @Temporal annotation");
            }

            return null;
        }
        return null;
    }

    private boolean isJavaUtilDate(Class<?> fc) {
        if (fc != null) {
            return !fc.isAssignableFrom(java.sql.Date.class) && fc.isAssignableFrom(java.util.Date.class);
        }
        return false;
    }

    private boolean isJavaUtilCalendar(Class<?> fc) {
        if (fc != null) {
            return fc.isAssignableFrom(Calendar.class);
        }
        return false;
    }

    private boolean isJavaSqlDate(Class<?> fc) {
        if (fc != null) {
            return fc.isAssignableFrom(java.sql.Date.class);
        }
        return false;
    }

    private boolean isJavaSqlTimestamp(Class<?> fc) {
        if (fc != null) {
            return fc.isAssignableFrom(Timestamp.class);
        }
        return false;
    }

    private boolean isJavaSqlTime(Class<?> fc) {
        if (fc != null) {
            return fc.isAssignableFrom(Time.class);
        }
        return false;
    }

    private Class<?> getType(String clazz, String fieldName) {
        try {
            final Class<?> c = Class.forName(clazz);
            final Field f = c.getDeclaredField(fieldName);
            return f.getType();
        } catch (Exception e) {
            LOG.error("Cannot get type from " + clazz + "." + fieldName, e);
        }
        return null;
    }

    private String getWarnMessageFragment(String columnType) {
        if (DATE.equals(columnType)) {
            return "Consider converting to java.util.Calendar or java.util.Date with a @Temporal(TemporalType.DATE) annotation";
        } else if (TIMESTAMP.equals(columnType)) {
            return "Consider converting to java.util.Calendar or java.util.Date with a @Temporal(TemporalType.TIMESTAMP) annotation";
        } else if (TIME.equals(columnType)) {
            return "Consider converting to java.util.Calendar or java.util.Date with a @Temporal(TemporalType.TIME) annotation";
        } else {
            return "Consider converting to java.util.Calendar or java.util.Date with a @Temporal annotation";
        }
    }
}