package org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.resolver;

import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.Node;
import japa.parser.ast.body.FieldDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.expr.ArrayInitializerExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.MemberValuePair;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.NormalAnnotationExpr;
import japa.parser.ast.expr.QualifiedNameExpr;
import japa.parser.ast.expr.StringLiteralExpr;
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ojb.broker.metadata.ClassDescriptor;
import org.apache.ojb.broker.metadata.CollectionDescriptor;
import org.apache.ojb.broker.metadata.DescriptorRepository;
import org.apache.ojb.broker.metadata.ObjectReferenceDescriptor;
import org.kuali.rice.devtools.jpa.eclipselink.conv.ojb.OjbUtil;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.ParserUtil;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.AnnotationResolver;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.Level;
import org.kuali.rice.devtools.jpa.eclipselink.conv.parser.helper.NodeData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OneToManyResolver implements AnnotationResolver {
    private static final Log LOG = LogFactory.getLog(OneToManyResolver.class);

    public static final String PACKAGE = "javax.persistence";
    public static final String SIMPLE_NAME = "OneToMany";

    private final Collection<DescriptorRepository> descriptorRepositories;

    public OneToManyResolver(Collection<DescriptorRepository> descriptorRepositories) {
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
                return getAnnotationNodes(fullyQualifiedClass, ParserUtil.getFieldName(field), dclr);
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
            return cd.getCollectionDescriptorByName(fieldName) != null;
        }
        return false;
    }

    /** gets the annotation but also adds an import in the process if a Convert annotation is required. */
    private NodeData getAnnotationNodes(String clazz, String fieldName, TypeDeclaration dclr) {
        final ClassDescriptor cd = OjbUtil.findClassDescriptor(clazz, descriptorRepositories);
        if (cd != null) {
            List<MemberValuePair> pairs = new ArrayList<MemberValuePair>();
            Collection<ImportDeclaration> additionalImports = new ArrayList<ImportDeclaration>();

            final CollectionDescriptor cld = cd.getCollectionDescriptorByName(fieldName);
            if (cld != null) {
                if (cld.isMtoNRelation()) {
                    return null;
                }

                final String[] fkToItemClass = getFksToItemClass(cld);
                if (fkToItemClass != null || fkToItemClass.length != 0) {
                    LOG.warn(clazz + "." + fieldName + " field has a collection descriptor for " + fieldName
                            + " for a 1:M relationship but has fk-pointing-to-element-class configured");
                }

                final String[] fkToThisClass = getFksToThisClass(cld);
                if (fkToThisClass != null || fkToThisClass.length != 0) {
                    LOG.warn(clazz + "." + fieldName + " field has a collection descriptor for " + fieldName
                            + " for a 1:N relationship but does not have any fk-pointing-to-this-class configured");
                }

                final Collection<String> fks = cld.getForeignKeyFields();
                if (fks == null || fks.isEmpty()) {
                    LOG.error(clazz + "." + fieldName + " field has a collection descriptor for " + fieldName
                            + " but does not have any foreign keys configured");
                    return null;
                }

                final String className = cld.getItemClassName();
                if (StringUtils.isBlank(className)) {
                    LOG.error(clazz + "." + fieldName + " field has a reference descriptor for " + fieldName
                            + " but does not class name attribute");
                } else {
                    final String shortClassName = ClassUtils.getShortClassName(className);
                    final String packageName = ClassUtils.getPackageName(className);
                    pairs.add(new MemberValuePair("targetEntity", new NameExpr(shortClassName + ".class")));
                    additionalImports.add(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(packageName), shortClassName), false, false));
                }

                final boolean proxy = cld.isLazy();
                if (proxy) {
                    pairs.add(new MemberValuePair("fetch", new StringLiteralExpr("Fetch.LAZY")));
                    additionalImports.add(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), "FetchType"), false, false));
                }

                final int proxyPfl = cld.getProxyPrefetchingLimit();
                if (proxyPfl > 0) {
                    LOG.error(clazz + "." + fieldName + " field has a proxy prefetch limit of " + proxyPfl + ", unsupported conversion to @OneToOne attributes");
                }

                final boolean refresh = cld.isRefresh();
                if (refresh) {
                    LOG.error(clazz + "." + fieldName + " field has refresh set to " + refresh + ", unsupported conversion to @OneToOne attributes");
                }

                final List<Expression> cascadeTypes = new ArrayList<Expression>();
                final boolean autoRetrieve = cld.getCascadeRetrieve();
                if (autoRetrieve) {
                    cascadeTypes.add(new NameExpr("CascadeType.REFRESH"));
                } else {
                    LOG.warn(clazz + "." + fieldName + " field has auto-retrieve set to " + autoRetrieve + ", unsupported conversion to CascadeType");
                }

                final int autoDelete = cld.getCascadingDelete();
                if (autoDelete == ObjectReferenceDescriptor.CASCADE_NONE) {
                    LOG.warn(clazz + "." + fieldName + " field has auto-delete set to none, unsupported conversion to CascadeType");
                } else if (autoDelete == ObjectReferenceDescriptor.CASCADE_LINK) {
                    LOG.warn(clazz + "." + fieldName + " field has auto-delete set to link, unsupported conversion to CascadeType");
                } else if (autoDelete == ObjectReferenceDescriptor.CASCADE_OBJECT) {
                    cascadeTypes.add(new NameExpr("CascadeType.REMOVE"));
                } else {
                    LOG.error(clazz + "." + fieldName + " field has auto-delete set to an invalid value");
                }

                final int autoUpdate = cld.getCascadingStore();
                if (autoUpdate == ObjectReferenceDescriptor.CASCADE_NONE) {
                    LOG.warn(clazz + "." + fieldName + " field has auto-update set to none, unsupported conversion to CascadeType");
                } else if (autoUpdate == ObjectReferenceDescriptor.CASCADE_LINK) {
                    LOG.warn(clazz + "." + fieldName + " field has auto-update set to link, unsupported conversion to CascadeType");
                } else if (autoUpdate == ObjectReferenceDescriptor.CASCADE_OBJECT) {
                    cascadeTypes.add(new NameExpr("CascadeType.PERSIST"));
                } else {
                    LOG.error(clazz + "." + fieldName + " field has auto-update set to an invalid value");
                }

                if (!cascadeTypes.isEmpty()) {
                    pairs.add(new MemberValuePair("cascade", new ArrayInitializerExpr(cascadeTypes)));
                    additionalImports.add(new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), "CascadeType"), false, false));
                }

                return new NodeData(new NormalAnnotationExpr(new NameExpr(SIMPLE_NAME), pairs),
                        new ImportDeclaration(new QualifiedNameExpr(new NameExpr(PACKAGE), SIMPLE_NAME), false, false),
                        additionalImports);
            }
        }
        return null;
    }

    private String[] getFksToItemClass(CollectionDescriptor cld) {
        try {
            return cld.getFksToItemClass();
        } catch (NullPointerException e) {
            return new String[] {};
        }
    }

    private String [] getFksToThisClass(CollectionDescriptor cld) {
        try {
            return cld.getFksToItemClass();
        } catch (NullPointerException e) {
            return new String[] {};
        }
    }
}