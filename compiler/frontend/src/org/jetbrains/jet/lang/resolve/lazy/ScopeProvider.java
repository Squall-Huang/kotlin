/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.lazy;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.ImportsResolver;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;
import org.jetbrains.jet.lang.resolve.scopes.RedeclarationHandler;
import org.jetbrains.jet.lang.resolve.scopes.WritableScope;
import org.jetbrains.jet.lang.resolve.scopes.WritableScopeImpl;

import java.util.List;

/**
* @author abreslav
*/
public class ScopeProvider {

    private final ResolveSession resolveSession;

    public ScopeProvider(@NotNull ResolveSession resolveSession) {
        this.resolveSession = resolveSession;
    }

    // This scope does not contain imported functions
    @NotNull
    public JetScope getFileScopeForDeclarationResolution(JetFile file) {
        // package
        JetNamespaceHeader header = file.getNamespaceHeader();
        if (header == null) {
            throw new IllegalArgumentException("Scripts are not supported: " + file.getName());
        }

        FqName fqName = new FqName(header.getQualifiedName());
        NamespaceDescriptor packageDescriptor = resolveSession.getPackageDescriptorByFqName(fqName);

        if (packageDescriptor == null) {
            throw new IllegalStateException("Package not found: " + fqName + " maybe the file is not in scope of this resolve session: " + file.getName());
        }

        WritableScope writableScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING, "File scope for declaration resolution");
        writableScope.importScope(resolveSession.getPackageDescriptorByFqName(FqName.ROOT).getMemberScope());
        List<JetImportDirective> importDirectives = Lists.newArrayList();
        resolveSession.getModuleConfiguration().addDefaultImports(importDirectives);
        importDirectives.addAll(file.getImportDirectives());
        ImportsResolver.processImportsInFile(true, writableScope, importDirectives,
                                             resolveSession.getPackageDescriptorByFqName(FqName.ROOT).getMemberScope(),
                                             resolveSession.getModuleConfiguration(), resolveSession.getTrace(),
                                             resolveSession.getInjector().getQualifiedExpressionResolver());
        writableScope.importScope(packageDescriptor.getMemberScope());

        writableScope.changeLockLevel(WritableScope.LockLevel.READING);
        // TODO: Cache
        return writableScope;
    }

    @NotNull
    public JetScope getResolutionScopeForDeclaration(@NotNull JetDeclaration jetDeclaration) {
        PsiElement immediateParent = jetDeclaration.getParent();
        if (immediateParent instanceof JetFile) {
            return getFileScopeForDeclarationResolution((JetFile) immediateParent);
        }

        JetDeclaration parentDeclaration = PsiTreeUtil.getParentOfType(jetDeclaration, JetDeclaration.class);
        if (parentDeclaration instanceof JetClassOrObject) {
            JetClassOrObject classOrObject = (JetClassOrObject) parentDeclaration;
            LazyClassDescriptor classDescriptor = (LazyClassDescriptor) resolveSession.getClassDescriptor(classOrObject);
            if (jetDeclaration instanceof JetEnumEntry) {
                return ((LazyClassDescriptor) classDescriptor.getClassObjectDescriptor()).getScopeForMemberDeclarationResolution();
            }
            return classDescriptor.getScopeForMemberDeclarationResolution();
        }
        else if (parentDeclaration instanceof JetClassObject) {
            JetClassObject classObject = (JetClassObject) parentDeclaration;
            LazyClassDescriptor classObjectDescriptor = resolveSession.getClassObjectDescriptor(classObject);
            return classObjectDescriptor.getScopeForMemberDeclarationResolution();
        }
        else {
            throw new IllegalStateException("Don't call this method for local declarations: " + jetDeclaration + " " + jetDeclaration.getText());
        }
    }
}
