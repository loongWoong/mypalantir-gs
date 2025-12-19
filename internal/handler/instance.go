package handler

import (
	"net/http"
	"strconv"

	"mypalantir/internal/service"

	"github.com/gin-gonic/gin"
)

// InstanceHandler 实例处理器
type InstanceHandler struct {
	instanceService *service.InstanceService
}

// NewInstanceHandler 创建实例处理器
func NewInstanceHandler(instanceService *service.InstanceService) *InstanceHandler {
	return &InstanceHandler{
		instanceService: instanceService,
	}
}

// CreateInstance 创建实例
func (h *InstanceHandler) CreateInstance(c *gin.Context) {
	objectType := c.Param("object_type")

	var data map[string]interface{}
	if err := c.ShouldBindJSON(&data); err != nil {
		Error(c, http.StatusBadRequest, "invalid request body")
		return
	}

	id, err := h.instanceService.CreateInstance(objectType, data)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, map[string]string{"id": id})
}

// GetInstance 获取实例详情
func (h *InstanceHandler) GetInstance(c *gin.Context) {
	objectType := c.Param("object_type")
	id := c.Param("id")

	instance, err := h.instanceService.GetInstance(objectType, id)
	if err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}

	Success(c, instance)
}

// UpdateInstance 更新实例
func (h *InstanceHandler) UpdateInstance(c *gin.Context) {
	objectType := c.Param("object_type")
	id := c.Param("id")

	var data map[string]interface{}
	if err := c.ShouldBindJSON(&data); err != nil {
		Error(c, http.StatusBadRequest, "invalid request body")
		return
	}

	if err := h.instanceService.UpdateInstance(objectType, id, data); err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, nil)
}

// DeleteInstance 删除实例
func (h *InstanceHandler) DeleteInstance(c *gin.Context) {
	objectType := c.Param("object_type")
	id := c.Param("id")

	if err := h.instanceService.DeleteInstance(objectType, id); err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}

	Success(c, nil)
}

// ListInstances 列出实例
func (h *InstanceHandler) ListInstances(c *gin.Context) {
	objectType := c.Param("object_type")

	// 解析分页参数
	offset, _ := strconv.Atoi(c.DefaultQuery("offset", "0"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))

	// 解析过滤参数（简化处理）
	filters := make(map[string]interface{})
	for key, values := range c.Request.URL.Query() {
		if key != "offset" && key != "limit" && len(values) > 0 {
			filters[key] = values[0]
		}
	}

	instances, total, err := h.instanceService.ListInstances(objectType, offset, limit, filters)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, map[string]interface{}{
		"items":  instances,
		"total":  total,
		"offset": offset,
		"limit":  limit,
	})
}
