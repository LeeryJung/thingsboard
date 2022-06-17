/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
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
package org.thingsboard.server.service.sync.ie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNode;
import org.thingsboard.rule.engine.debug.TbMsgGeneratorNodeConfiguration;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.sync.ie.DeviceExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportSettings;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.common.data.sync.ie.EntityImportSettings;
import org.thingsboard.server.common.data.sync.ie.RuleChainExportData;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceProfileDao;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.service.action.EntityActionService;
import org.thingsboard.server.service.ota.OtaPackageStateService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;

@DaoSqlTest
public class ExportImportServiceSqlTest extends BaseExportImportServiceTest {

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;
    @SpyBean
    private EntityActionService entityActionService;
    @SpyBean
    private OtaPackageStateService otaPackageStateService;

    @Test
    public void testExportImportAsset_betweenTenants() throws Exception {
        Asset asset = createAsset(tenantId1, null, "AB", "Asset of tenant 1");
        EntityExportData<Asset> exportData = exportEntity(tenantAdmin1, asset.getId());

        EntityImportResult<Asset> importResult = importEntity(tenantAdmin2, exportData);
        checkImportedEntity(tenantId1, asset, tenantId2, importResult.getSavedEntity());
        checkImportedAssetData(asset, importResult.getSavedEntity());
    }

    @Test
    public void testExportImportAsset_sameTenant() throws Exception {
        Asset asset = createAsset(tenantId1, null, "AB", "Asset v1.0");
        EntityExportData<Asset> exportData = exportEntity(tenantAdmin1, asset.getId());

        EntityImportResult<Asset> importResult = importEntity(tenantAdmin1, exportData);
        checkImportedEntity(tenantId1, asset, tenantId1, importResult.getSavedEntity());
        checkImportedAssetData(asset, importResult.getSavedEntity());
    }

    @Test
    public void testExportImportAsset_sameTenant_withCustomer() throws Exception {
        Customer customer = createCustomer(tenantId1, "My customer");
        Asset asset = createAsset(tenantId1, customer.getId(), "AB", "My asset");

        Asset importedAsset = importEntity(tenantAdmin1, this.<Asset, AssetId>exportEntity(tenantAdmin1, asset.getId())).getSavedEntity();
        assertThat(importedAsset.getCustomerId()).isEqualTo(asset.getCustomerId());
    }


    @Test
    public void testExportImportCustomer_betweenTenants() throws Exception {
        Customer customer = createCustomer(tenantAdmin1.getTenantId(), "Customer of tenant 1");
        EntityExportData<Customer> exportData = exportEntity(tenantAdmin1, customer.getId());

        EntityImportResult<Customer> importResult = importEntity(tenantAdmin2, exportData);
        checkImportedEntity(tenantId1, customer, tenantId2, importResult.getSavedEntity());
        checkImportedCustomerData(customer, importResult.getSavedEntity());
    }

    @Test
    public void testExportImportCustomer_sameTenant() throws Exception {
        Customer customer = createCustomer(tenantAdmin1.getTenantId(), "Customer v1.0");
        EntityExportData<Customer> exportData = exportEntity(tenantAdmin1, customer.getId());

        EntityImportResult<Customer> importResult = importEntity(tenantAdmin1, exportData);
        checkImportedEntity(tenantId1, customer, tenantId1, importResult.getSavedEntity());
        checkImportedCustomerData(customer, importResult.getSavedEntity());
    }


    @Test
    public void testExportImportDeviceWithProfile_betweenTenants() throws Exception {
        DeviceProfile deviceProfile = createDeviceProfile(tenantId1, null, null, "Device profile of tenant 1");
        Device device = createDevice(tenantId1, null, deviceProfile.getId(), "Device of tenant 1");
        DeviceCredentials credentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId1, device.getId());

        EntityExportData<DeviceProfile> profileExportData = exportEntity(tenantAdmin1, deviceProfile.getId());

        EntityExportData<Device> deviceExportData = exportEntity(tenantAdmin1, device.getId());
        DeviceCredentials exportedCredentials = ((DeviceExportData) deviceExportData).getCredentials();
        exportedCredentials.setCredentialsId(credentials.getCredentialsId() + "a");

        EntityImportResult<DeviceProfile> profileImportResult = importEntity(tenantAdmin2, profileExportData);
        checkImportedEntity(tenantId1, deviceProfile, tenantId2, profileImportResult.getSavedEntity());
        checkImportedDeviceProfileData(deviceProfile, profileImportResult.getSavedEntity());

        EntityImportResult<Device> deviceImportResult = importEntity(tenantAdmin2, deviceExportData);
        Device importedDevice = deviceImportResult.getSavedEntity();
        checkImportedEntity(tenantId1, device, tenantId2, deviceImportResult.getSavedEntity());
        checkImportedDeviceData(device, importedDevice);

        assertThat(importedDevice.getDeviceProfileId()).isEqualTo(profileImportResult.getSavedEntity().getId());

        DeviceCredentials importedCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId2, importedDevice.getId());
        assertThat(importedCredentials.getId()).isNotEqualTo(credentials.getId());
        assertThat(importedCredentials.getCredentialsId()).isEqualTo(exportedCredentials.getCredentialsId());
        assertThat(importedCredentials.getCredentialsValue()).isEqualTo(credentials.getCredentialsValue());
        assertThat(importedCredentials.getCredentialsType()).isEqualTo(credentials.getCredentialsType());
    }

    @Test
    public void testExportImportDevice_sameTenant() throws Exception {
        DeviceProfile deviceProfile = createDeviceProfile(tenantId1, null, null, "Device profile v1.0");
        OtaPackage firmware = createOtaPackage(tenantId1, deviceProfile.getId(), OtaPackageType.FIRMWARE);
        OtaPackage software = createOtaPackage(tenantId1, deviceProfile.getId(), OtaPackageType.SOFTWARE);
        Device device = createDevice(tenantId1, null, deviceProfile.getId(), "Device v1.0");
        device.setFirmwareId(firmware.getId());
        device.setSoftwareId(software.getId());
        device = deviceService.saveDevice(device);

        DeviceCredentials credentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId1, device.getId());

        EntityExportData<Device> deviceExportData = exportEntity(tenantAdmin1, device.getId());

        EntityImportResult<Device> importResult = importEntity(tenantAdmin1, deviceExportData);
        Device importedDevice = importResult.getSavedEntity();

        checkImportedEntity(tenantId1, device, tenantId1, importResult.getSavedEntity());
        assertThat(importedDevice.getDeviceProfileId()).isEqualTo(device.getDeviceProfileId());
        assertThat(deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId1, device.getId())).isEqualTo(credentials);
        assertThat(importedDevice.getFirmwareId()).isEqualTo(firmware.getId());
        assertThat(importedDevice.getSoftwareId()).isEqualTo(software.getId());
    }


    @Test
    public void testExportImportDashboard_betweenTenants() throws Exception {
        Dashboard dashboard = createDashboard(tenantAdmin1.getTenantId(), null, "Dashboard of tenant 1");
        EntityExportData<Dashboard> exportData = exportEntity(tenantAdmin1, dashboard.getId());

        EntityImportResult<Dashboard> importResult = importEntity(tenantAdmin2, exportData);
        checkImportedEntity(tenantId1, dashboard, tenantId2, importResult.getSavedEntity());
        checkImportedDashboardData(dashboard, importResult.getSavedEntity());
    }

    @Test
    public void testExportImportDashboard_sameTenant() throws Exception {
        Dashboard dashboard = createDashboard(tenantAdmin1.getTenantId(), null, "Dashboard v1.0");
        EntityExportData<Dashboard> exportData = exportEntity(tenantAdmin1, dashboard.getId());

        EntityImportResult<Dashboard> importResult = importEntity(tenantAdmin1, exportData);
        checkImportedEntity(tenantId1, dashboard, tenantId1, importResult.getSavedEntity());
        checkImportedDashboardData(dashboard, importResult.getSavedEntity());
    }

    @Test
    public void testExportImportDashboard_betweenTenants_withCustomer_updated() throws Exception {
        Dashboard dashboard = createDashboard(tenantAdmin1.getTenantId(), null, "Dashboard of tenant 1");
        EntityExportData<Dashboard> exportData = exportEntity(tenantAdmin1, dashboard.getId());

        Dashboard importedDashboard = importEntity(tenantAdmin2, exportData).getSavedEntity();
        checkImportedEntity(tenantId1, dashboard, tenantId2, importedDashboard);

        Customer customer = createCustomer(tenantId1, "Customer 1");
        EntityExportData<Customer> customerExportData = exportEntity(tenantAdmin1, customer.getId());
        dashboardService.assignDashboardToCustomer(tenantId1, dashboard.getId(), customer.getId());
        exportData = exportEntity(tenantAdmin1, dashboard.getId());

        Customer importedCustomer = importEntity(tenantAdmin2, customerExportData).getSavedEntity();
        importedDashboard = importEntity(tenantAdmin2, exportData).getSavedEntity();
        assertThat(importedDashboard.getAssignedCustomers()).hasOnlyOneElementSatisfying(customerInfo -> {
            assertThat(customerInfo.getCustomerId()).isEqualTo(importedCustomer.getId());
        });
    }

    @Test
    public void testExportImportDashboard_betweenTenants_withEntityAliases() throws Exception {
        Asset asset1 = createAsset(tenantId1, null, "A", "Asset 1");
        Asset asset2 = createAsset(tenantId1, null, "A", "Asset 2");
        Dashboard dashboard = createDashboard(tenantId1, null, "Dashboard 1");

        String entityAliases = "{\n" +
                "\t\"23c4185d-1497-9457-30b2-6d91e69a5b2c\": {\n" +
                "\t\t\"alias\": \"assets\",\n" +
                "\t\t\"filter\": {\n" +
                "\t\t\t\"entityList\": [\n" +
                "\t\t\t\t\"" + asset1.getId().toString() + "\",\n" +
                "\t\t\t\t\"" + asset2.getId().toString() + "\"\n" +
                "\t\t\t],\n" +
                "\t\t\t\"entityType\": \"ASSET\",\n" +
                "\t\t\t\"resolveMultiple\": true,\n" +
                "\t\t\t\"type\": \"entityList\"\n" +
                "\t\t},\n" +
                "\t\t\"id\": \"23c4185d-1497-9457-30b2-6d91e69a5b2c\"\n" +
                "\t}\n" +
                "}";
        ObjectNode dashboardConfiguration = JacksonUtil.newObjectNode();
        dashboardConfiguration.set("entityAliases", JacksonUtil.toJsonNode(entityAliases));
        dashboardConfiguration.set("description", new TextNode("hallo"));
        dashboard.setConfiguration(dashboardConfiguration);
        dashboard = dashboardService.saveDashboard(dashboard);

        EntityExportData<Asset> asset1ExportData = exportEntity(tenantAdmin1, asset1.getId());
        EntityExportData<Asset> asset2ExportData = exportEntity(tenantAdmin1, asset2.getId());
        EntityExportData<Dashboard> dashboardExportData = exportEntity(tenantAdmin1, dashboard.getId());

        Asset importedAsset1 = importEntity(tenantAdmin2, asset1ExportData).getSavedEntity();
        Asset importedAsset2 = importEntity(tenantAdmin2, asset2ExportData).getSavedEntity();
        Dashboard importedDashboard = importEntity(tenantAdmin2, dashboardExportData).getSavedEntity();

        Set<String> entityAliasEntitiesIds = Streams.stream(importedDashboard.getConfiguration()
                        .get("entityAliases").elements().next().get("filter").get("entityList").elements())
                .map(JsonNode::asText).collect(Collectors.toSet());
        assertThat(entityAliasEntitiesIds).doesNotContain(asset1.getId().toString(), asset2.getId().toString());
        assertThat(entityAliasEntitiesIds).contains(importedAsset1.getId().toString(), importedAsset2.getId().toString());
    }


    @Test
    public void testExportImportRuleChain_betweenTenants() throws Exception {
        RuleChain ruleChain = createRuleChain(tenantId1, "Rule chain of tenant 1");
        RuleChainMetaData metaData = ruleChainService.loadRuleChainMetaData(tenantId1, ruleChain.getId());
        EntityExportData<RuleChain> exportData = exportEntity(tenantAdmin1, ruleChain.getId());

        EntityImportResult<RuleChain> importResult = importEntity(tenantAdmin2, exportData);
        RuleChain importedRuleChain = importResult.getSavedEntity();
        RuleChainMetaData importedMetaData = ruleChainService.loadRuleChainMetaData(tenantId2, importedRuleChain.getId());

        checkImportedEntity(tenantId1, ruleChain, tenantId2, importResult.getSavedEntity());
        checkImportedRuleChainData(ruleChain, metaData, importedRuleChain, importedMetaData);
    }

    @Test
    public void testExportImportRuleChain_sameTenant() throws Exception {
        RuleChain ruleChain = createRuleChain(tenantId1, "Rule chain v1.0");
        RuleChainMetaData metaData = ruleChainService.loadRuleChainMetaData(tenantId1, ruleChain.getId());
        EntityExportData<RuleChain> exportData = exportEntity(tenantAdmin1, ruleChain.getId());

        EntityImportResult<RuleChain> importResult = importEntity(tenantAdmin1, exportData);
        RuleChain importedRuleChain = importResult.getSavedEntity();
        RuleChainMetaData importedMetaData = ruleChainService.loadRuleChainMetaData(tenantId1, importedRuleChain.getId());

        checkImportedEntity(tenantId1, ruleChain, tenantId1, importResult.getSavedEntity());
        checkImportedRuleChainData(ruleChain, metaData, importedRuleChain, importedMetaData);
    }


    @Test
    public void testExportImportWithInboundRelations_betweenTenants() throws Exception {
        Asset asset = createAsset(tenantId1, null, "A", "Asset 1");
        Device device = createDevice(tenantId1, null, null, "Device 1");
        EntityRelation relation = createRelation(asset.getId(), device.getId());

        EntityExportData<Asset> assetExportData = exportEntity(tenantAdmin1, asset.getId());
        EntityExportData<Device> deviceExportData = exportEntity(tenantAdmin1, device.getId(), EntityExportSettings.builder()
                .exportRelations(true)
                .exportCredentials(false)
                .build());

        assertThat(deviceExportData.getRelations()).size().isOne();
        assertThat(deviceExportData.getRelations().get(0)).matches(entityRelation -> {
            return entityRelation.getFrom().equals(asset.getId()) && entityRelation.getTo().equals(device.getId());
        });
        ((Device) deviceExportData.getEntity()).setDeviceProfileId(null);

        Asset importedAsset = importEntity(tenantAdmin2, assetExportData).getSavedEntity();
        Device importedDevice = importEntity(tenantAdmin2, deviceExportData, EntityImportSettings.builder()
                .updateRelations(true)
                .build()).getSavedEntity();
        checkImportedEntity(tenantId1, device, tenantId2, importedDevice);
        checkImportedEntity(tenantId1, asset, tenantId2, importedAsset);

        List<EntityRelation> importedRelations = relationService.findByTo(TenantId.SYS_TENANT_ID, importedDevice.getId(), RelationTypeGroup.COMMON);
        assertThat(importedRelations).size().isOne();
        assertThat(importedRelations.get(0)).satisfies(importedRelation -> {
            assertThat(importedRelation.getFrom()).isEqualTo(importedAsset.getId());
            assertThat(importedRelation.getType()).isEqualTo(relation.getType());
            assertThat(importedRelation.getAdditionalInfo()).isEqualTo(relation.getAdditionalInfo());
        });
    }

    @Test
    public void testExportImportWithRelations_betweenTenants() throws Exception {
        Asset asset = createAsset(tenantId1, null, "A", "Asset 1");
        Device device = createDevice(tenantId1, null, null, "Device 1");
        EntityRelation relation = createRelation(asset.getId(), device.getId());

        EntityExportData<Asset> assetExportData = exportEntity(tenantAdmin1, asset.getId());
        EntityExportData<Device> deviceExportData = exportEntity(tenantAdmin1, device.getId(), EntityExportSettings.builder()
                .exportRelations(true)
                .exportCredentials(false)
                .build());
        deviceExportData.getEntity().setDeviceProfileId(null);

        Asset importedAsset = importEntity(tenantAdmin2, assetExportData).getSavedEntity();
        Device importedDevice = importEntity(tenantAdmin2, deviceExportData, EntityImportSettings.builder()
                .updateRelations(true)
                .build()).getSavedEntity();

        List<EntityRelation> importedRelations = relationService.findByTo(TenantId.SYS_TENANT_ID, importedDevice.getId(), RelationTypeGroup.COMMON);
        assertThat(importedRelations).size().isOne();
        assertThat(importedRelations.get(0)).satisfies(importedRelation -> {
            assertThat(importedRelation.getFrom()).isEqualTo(importedAsset.getId());
            assertThat(importedRelation.getType()).isEqualTo(relation.getType());
            assertThat(importedRelation.getAdditionalInfo()).isEqualTo(relation.getAdditionalInfo());
        });
    }

    @Test
    public void testExportImportWithRelations_sameTenant() throws Exception {
        Asset asset = createAsset(tenantId1, null, "A", "Asset 1");
        Device device1 = createDevice(tenantId1, null, null, "Device 1");
        EntityRelation relation1 = createRelation(asset.getId(), device1.getId());

        EntityExportData<Asset> assetExportData = exportEntity(tenantAdmin1, asset.getId(), EntityExportSettings.builder()
                .exportRelations(true)
                .build());
        assertThat(assetExportData.getRelations()).size().isOne();

        Device device2 = createDevice(tenantId1, null, null, "Device 2");
        EntityRelation relation2 = createRelation(asset.getId(), device2.getId());

        importEntity(tenantAdmin1, assetExportData, EntityImportSettings.builder()
                .updateRelations(true)
                .build());

        List<EntityRelation> relations = relationService.findByFrom(TenantId.SYS_TENANT_ID, asset.getId(), RelationTypeGroup.COMMON);
        assertThat(relations).contains(relation1);
        assertThat(relations).doesNotContain(relation2);
    }

    @Test
    public void textExportImportWithRelations_sameTenant_removeExisting() throws Exception {
        Asset asset1 = createAsset(tenantId1, null, "A", "Asset 1");
        Device device = createDevice(tenantId1, null, null, "Device 1");
        EntityRelation relation1 = createRelation(asset1.getId(), device.getId());

        EntityExportData<Device> deviceExportData = exportEntity(tenantAdmin1, device.getId(), EntityExportSettings.builder()
                .exportRelations(true)
                .build());
        assertThat(deviceExportData.getRelations()).size().isOne();

        Asset asset2 = createAsset(tenantId1, null, "A", "Asset 2");
        EntityRelation relation2 = createRelation(asset2.getId(), device.getId());

        importEntity(tenantAdmin1, deviceExportData, EntityImportSettings.builder()
                .updateRelations(true)
                .build());

        List<EntityRelation> relations = relationService.findByTo(TenantId.SYS_TENANT_ID, device.getId(), RelationTypeGroup.COMMON);
        assertThat(relations).contains(relation1);
        assertThat(relations).doesNotContain(relation2);
    }


    @Test
    public void testExportImportDeviceProfile_betweenTenants_findExistingByName() throws Exception {
        DeviceProfile defaultDeviceProfile = deviceProfileService.findDefaultDeviceProfile(tenantId1);
        EntityExportData<DeviceProfile> deviceProfileExportData = exportEntity(tenantAdmin1, defaultDeviceProfile.getId());

        assertThatThrownBy(() -> {
            importEntity(tenantAdmin2, deviceProfileExportData, EntityImportSettings.builder()
                    .findExistingByName(false)
                    .build());
        }).hasMessageContaining("default device profile is present");

        importEntity(tenantAdmin2, deviceProfileExportData, EntityImportSettings.builder()
                .findExistingByName(true)
                .build());
        checkImportedEntity(tenantId1, defaultDeviceProfile, tenantId2, deviceProfileService.findDefaultDeviceProfile(tenantId2));
    }


    @Test
    public void testEntityEventsOnImport() throws Exception {
        Customer customer = createCustomer(tenantId1, "Customer 1");
        Asset asset = createAsset(tenantId1, null, "A", "Asset 1");
        RuleChain ruleChain = createRuleChain(tenantId1, "Rule chain 1");
        Dashboard dashboard = createDashboard(tenantId1, null, "Dashboard 1");
        DeviceProfile deviceProfile = createDeviceProfile(tenantId1, ruleChain.getId(), dashboard.getId(), "Device profile 1");
        Device device = createDevice(tenantId1, null, deviceProfile.getId(), "Device 1");

        Map<EntityType, EntityExportData> entitiesExportData = Stream.of(customer.getId(), asset.getId(), device.getId(),
                        ruleChain.getId(), dashboard.getId(), deviceProfile.getId())
                .map(entityId -> {
                    try {
                        return exportEntity(tenantAdmin1, entityId, EntityExportSettings.builder()
                                .exportCredentials(false)
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toMap(EntityExportData::getEntityType, d -> d));

        Customer importedCustomer = (Customer) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.CUSTOMER)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedCustomer.getId()), eq(importedCustomer),
                any(), eq(ActionType.ADDED), isNull());
        importEntity(tenantAdmin2, entitiesExportData.get(EntityType.CUSTOMER));
        verify(entityActionService).logEntityAction(any(), eq(importedCustomer.getId()), eq(importedCustomer),
                any(), eq(ActionType.UPDATED), isNull());
        verify(tbClusterService).sendNotificationMsgToEdgeService(any(), any(), eq(importedCustomer.getId()), any(), any(), eq(EdgeEventActionType.UPDATED));

        Asset importedAsset = (Asset) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.ASSET)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedAsset.getId()), eq(importedAsset),
                any(), eq(ActionType.ADDED), isNull());
        importEntity(tenantAdmin2, entitiesExportData.get(EntityType.ASSET));
        verify(entityActionService).logEntityAction(any(), eq(importedAsset.getId()), eq(importedAsset),
                any(), eq(ActionType.UPDATED), isNull());
        verify(tbClusterService).sendNotificationMsgToEdgeService(any(), any(), eq(importedAsset.getId()), any(), any(), eq(EdgeEventActionType.UPDATED));

        RuleChain importedRuleChain = (RuleChain) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.RULE_CHAIN)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedRuleChain.getId()), eq(importedRuleChain),
                any(), eq(ActionType.ADDED), isNull());
        verify(tbClusterService).broadcastEntityStateChangeEvent(any(), eq(importedRuleChain.getId()), eq(ComponentLifecycleEvent.CREATED));

        Dashboard importedDashboard = (Dashboard) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.DASHBOARD)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedDashboard.getId()), eq(importedDashboard),
                any(), eq(ActionType.ADDED), isNull());

        DeviceProfile importedDeviceProfile = (DeviceProfile) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.DEVICE_PROFILE)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedDeviceProfile.getId()), eq(importedDeviceProfile),
                any(), eq(ActionType.ADDED), isNull());
        verify(tbClusterService).onDeviceProfileChange(eq(importedDeviceProfile), any());
        verify(tbClusterService).broadcastEntityStateChangeEvent(any(), eq(importedDeviceProfile.getId()), eq(ComponentLifecycleEvent.CREATED));
        verify(tbClusterService).sendNotificationMsgToEdgeService(any(), any(), eq(importedDeviceProfile.getId()), any(), any(), eq(EdgeEventActionType.ADDED));
        verify(otaPackageStateService).update(eq(importedDeviceProfile), eq(false), eq(false));

        Device importedDevice = (Device) importEntity(tenantAdmin2, entitiesExportData.get(EntityType.DEVICE)).getSavedEntity();
        verify(entityActionService).logEntityAction(any(), eq(importedDevice.getId()), eq(importedDevice),
                any(), eq(ActionType.ADDED), isNull());
        verify(tbClusterService).onDeviceUpdated(eq(importedDevice), isNull());
        importEntity(tenantAdmin2, entitiesExportData.get(EntityType.DEVICE));
        verify(tbClusterService).onDeviceUpdated(eq(importedDevice), eq(importedDevice));
    }

    @Test
    public void testExternalIdsInExportData() throws Exception {
        Customer customer = createCustomer(tenantId1, "Customer 1");
        Asset asset = createAsset(tenantId1, customer.getId(), "A", "Asset 1");
        RuleChain ruleChain = createRuleChain(tenantId1, "Rule chain 1", asset.getId());
        Dashboard dashboard = createDashboard(tenantId1, customer.getId(), "Dashboard 1", asset.getId());
        DeviceProfile deviceProfile = createDeviceProfile(tenantId1, ruleChain.getId(), dashboard.getId(), "Device profile 1");
        Device device = createDevice(tenantId1, customer.getId(), deviceProfile.getId(), "Device 1");
        EntityView entityView = createEntityView(tenantId1, customer.getId(), device.getId(), "Entity view 1");

        Map<EntityId, EntityId> ids = new HashMap<>();
        for (EntityId entityId : List.of(customer.getId(), asset.getId(), ruleChain.getId(), dashboard.getId(),
                deviceProfile.getId(), device.getId(), entityView.getId(), ruleChain.getId(), dashboard.getId())) {
            EntityExportData exportData = exportEntity(getSecurityUser(tenantAdmin1), entityId);
            EntityImportResult importResult = importEntity(getSecurityUser(tenantAdmin2), exportData, EntityImportSettings.builder()
                    .saveCredentials(false)
                    .build());
            ids.put(entityId, (EntityId) importResult.getSavedEntity().getId());
        }

        Asset exportedAsset = (Asset) exportEntity(tenantAdmin2, (AssetId) ids.get(asset.getId())).getEntity();
        assertThat(exportedAsset.getCustomerId()).isEqualTo(customer.getId());

        EntityExportData<RuleChain> ruleChainExportData = exportEntity(tenantAdmin2, (RuleChainId) ids.get(ruleChain.getId()));
        TbMsgGeneratorNodeConfiguration exportedRuleNodeConfig = ((RuleChainExportData) ruleChainExportData).getMetaData().getNodes().stream()
                .filter(node -> node.getType().equals(TbMsgGeneratorNode.class.getName())).findFirst()
                .map(RuleNode::getConfiguration).map(config -> JacksonUtil.treeToValue(config, TbMsgGeneratorNodeConfiguration.class)).orElse(null);
        assertThat(exportedRuleNodeConfig.getOriginatorId()).isEqualTo(asset.getId().toString());

        Dashboard exportedDashboard = (Dashboard) exportEntity(tenantAdmin2, (DashboardId) ids.get(dashboard.getId())).getEntity();
        assertThat(exportedDashboard.getAssignedCustomers()).hasOnlyOneElementSatisfying(shortCustomerInfo -> {
            assertThat(shortCustomerInfo.getCustomerId()).isEqualTo(customer.getId());
        });

        DeviceProfile exportedDeviceProfile = (DeviceProfile) exportEntity(tenantAdmin2, (DeviceProfileId) ids.get(deviceProfile.getId())).getEntity();
        assertThat(exportedDeviceProfile.getDefaultRuleChainId()).isEqualTo(ruleChain.getId());
        assertThat(exportedDeviceProfile.getDefaultDashboardId()).isEqualTo(dashboard.getId());

        Device exportedDevice = (Device) exportEntity(tenantAdmin2, (DeviceId) ids.get(device.getId())).getEntity();
        assertThat(exportedDevice.getCustomerId()).isEqualTo(customer.getId());
        assertThat(exportedDevice.getDeviceProfileId()).isEqualTo(deviceProfile.getId());

        EntityView exportedEntityView = (EntityView) exportEntity(tenantAdmin2, (EntityViewId) ids.get(entityView.getId())).getEntity();
        assertThat(exportedEntityView.getCustomerId()).isEqualTo(customer.getId());
        assertThat(exportedEntityView.getEntityId()).isEqualTo(device.getId());
    }

}
