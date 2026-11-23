package br.com.subti.next.configuracao;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regras de dependência entre camadas (Arquitetura 4.3 e 11.4) verificadas no build. */
@AnalyzeClasses(packages = "br.com.subti.next.configuracao", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String BASE = "br.com.subti.next.configuracao";

    @ArchTest
    static final ArchRule regrasDeDominioSemDependenciasExternas = classes().that()
            .resideInAPackage(BASE + ".domain.regra..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + ".domain.regra..");

    @ArchTest
    static final ArchRule dominioNaoDependeDeOutrasCamadas = noClasses().that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".api..", BASE + ".application..",
                    BASE + ".infrastructure..", "org.springframework.web..");

    @ArchTest
    static final ArchRule aplicacaoNaoDependeDaApi = noClasses().that().resideInAPackage(BASE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".api..", BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule apiNaoAcessaInfraestrutura = noClasses().that().resideInAPackage(BASE + ".api..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + ".infrastructure..", BASE + ".domain.repositorio..");

    @ArchTest
    static final ArchRule semAcessoAOutrosModulos = noClasses().that().resideInAPackage(BASE + "..")
            .should().dependOnClassesThat().resideInAnyPackage("br.com.subti.next.bff..", "br.com.subti.next.portfolio..",
                    "br.com.subti.next.sustentacao..", "br.com.subti.next.engenharia..");
}
