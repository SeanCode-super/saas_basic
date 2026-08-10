package com.saasbasics.platform.modules.organization.internal.application;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.AssignmentMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.EngagementMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgUnitMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgUnitRelationMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrganizationCapabilityMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrganizationClassificationMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrganizationMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrganizationRelationMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.PersonMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgPositionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapperAccess {

    private final ObjectProvider<OrganizationMapper> organizations;
    private final ObjectProvider<OrganizationClassificationMapper> classifications;
    private final ObjectProvider<OrganizationCapabilityMapper> capabilities;
    private final ObjectProvider<OrganizationRelationMapper> organizationRelations;
    private final ObjectProvider<OrgUnitMapper> units;
    private final ObjectProvider<OrgUnitRelationMapper> unitRelations;
    private final ObjectProvider<PersonMapper> persons;
    private final ObjectProvider<EngagementMapper> engagements;
    private final ObjectProvider<OrgPositionMapper> positions;
    private final ObjectProvider<AssignmentMapper> assignments;

    public OrganizationMapperAccess(ObjectProvider<OrganizationMapper> organizations,
                                    ObjectProvider<OrganizationClassificationMapper> classifications,
                                    ObjectProvider<OrganizationCapabilityMapper> capabilities,
                                    ObjectProvider<OrganizationRelationMapper> organizationRelations,
                                    ObjectProvider<OrgUnitMapper> units,
                                    ObjectProvider<OrgUnitRelationMapper> unitRelations,
                                    ObjectProvider<PersonMapper> persons,
                                    ObjectProvider<EngagementMapper> engagements,
                                    ObjectProvider<OrgPositionMapper> positions,
                                    ObjectProvider<AssignmentMapper> assignments) {
        this.organizations = organizations;
        this.classifications = classifications;
        this.capabilities = capabilities;
        this.organizationRelations = organizationRelations;
        this.units = units;
        this.unitRelations = unitRelations;
        this.persons = persons;
        this.engagements = engagements;
        this.positions = positions;
        this.assignments = assignments;
    }

    public OrganizationMapper organizations() {
        return required(organizations);
    }

    public OrganizationClassificationMapper classifications() {
        return required(classifications);
    }

    public OrganizationCapabilityMapper capabilities() {
        return required(capabilities);
    }

    public OrganizationRelationMapper organizationRelations() {
        return required(organizationRelations);
    }

    public OrgUnitMapper units() {
        return required(units);
    }

    public OrgUnitRelationMapper unitRelations() {
        return required(unitRelations);
    }

    public PersonMapper persons() {
        return required(persons);
    }

    public EngagementMapper engagements() {
        return required(engagements);
    }

    public OrgPositionMapper positions() {
        return required(positions);
    }

    public AssignmentMapper assignments() {
        return required(assignments);
    }

    private <T> T required(ObjectProvider<T> provider) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Organization operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
