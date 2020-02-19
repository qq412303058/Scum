package io.metersphere.service;

import io.metersphere.base.domain.UserRole;
import io.metersphere.base.domain.Workspace;
import io.metersphere.base.domain.WorkspaceExample;
import io.metersphere.base.mapper.WorkspaceMapper;
import io.metersphere.base.mapper.ext.ExtUserRoleMapper;
import io.metersphere.commons.constants.RoleConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.dto.UserRoleHelpDTO;
import io.metersphere.user.SessionUser;
import io.metersphere.user.SessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class WorkspaceService {
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private ExtUserRoleMapper extUserRoleMapper;

    public Workspace saveWorkspace(Workspace workspace) {
        if (StringUtils.isBlank(workspace.getName())) {
            MSException.throwException("Workspace name cannot be null.");
        }
        // set organization id
        workspace.setOrganizationId(SessionUtils.getCurrentOrganizationId());

        long currentTime = System.currentTimeMillis();
        if (StringUtils.isBlank(workspace.getId())) {
            WorkspaceExample example = new WorkspaceExample();
            example.createCriteria()
                    .andOrganizationIdEqualTo(SessionUtils.getCurrentOrganizationId())
                    .andNameEqualTo(workspace.getName());
            if (workspaceMapper.countByExample(example) > 0) {
                MSException.throwException("The workspace name already exists");
            }
            workspace.setId(UUID.randomUUID().toString()); // 设置ID
            workspace.setCreateTime(currentTime);
            workspace.setUpdateTime(currentTime); // 首次 update time
            workspaceMapper.insertSelective(workspace);
        } else {
            workspace.setUpdateTime(currentTime);
            workspaceMapper.updateByPrimaryKeySelective(workspace);
        }
        return workspace;
    }

    public List<Workspace> getWorkspaceList() {
        return workspaceMapper.selectByExample(null);
    }

    public void deleteWorkspace(String workspaceId) {
        workspaceMapper.deleteByPrimaryKey(workspaceId);
    }

    public void checkOwner(String workspaceId) {
        SessionUser user = SessionUtils.getUser();
        List<String> orgIds = user.getUserRoles().stream()
                .filter(ur -> RoleConstants.ORG_ADMIN.equals(ur.getRoleId()))
                .map(UserRole::getSourceId)
                .collect(Collectors.toList());
        WorkspaceExample example = new WorkspaceExample();
        example.createCriteria()
                .andOrganizationIdIn(orgIds)
                .andIdEqualTo(workspaceId);
        if (workspaceMapper.countByExample(example) == 0) {
            MSException.throwException("The current workspace does not belong to the current user");
        }
    }

    public List<Workspace> getWorkspaceListByUserId(String userId) {
        List<UserRoleHelpDTO> userRoleHelpList = extUserRoleMapper.getUserRoleHelpList(userId);
        List<String> workspaceIds = new ArrayList<>();
        userRoleHelpList.forEach(r -> {
            if (!StringUtils.isEmpty(r.getParentId())) {
                workspaceIds.add(r.getSourceId());
            }
        });
        WorkspaceExample workspaceExample = new WorkspaceExample();
        workspaceExample.createCriteria().andIdIn(workspaceIds);
        return workspaceMapper.selectByExample(workspaceExample);
    }

}
