package handler

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"mypalantir/internal/service"
)

// LinkHandler 关系处理器
type LinkHandler struct {
	linkService *service.LinkService
}

// NewLinkHandler 创建关系处理器
func NewLinkHandler(linkService *service.LinkService) *LinkHandler {
	return &LinkHandler{
		linkService: linkService,
	}
}

// CreateLink 创建关系
func (h *LinkHandler) CreateLink(c *gin.Context) {
	linkType := c.Param("link_type")

	var req struct {
		SourceID   string                 `json:"source_id" binding:"required"`
		TargetID   string                 `json:"target_id" binding:"required"`
		Properties map[string]interface{} `json:"properties,omitempty"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		Error(c, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Properties == nil {
		req.Properties = make(map[string]interface{})
	}

	id, err := h.linkService.CreateLink(linkType, req.SourceID, req.TargetID, req.Properties)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, map[string]string{"id": id})
}

// GetLink 获取关系详情
func (h *LinkHandler) GetLink(c *gin.Context) {
	linkType := c.Param("link_type")
	id := c.Param("id")

	link, err := h.linkService.GetLink(linkType, id)
	if err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}

	Success(c, link)
}

// UpdateLink 更新关系
func (h *LinkHandler) UpdateLink(c *gin.Context) {
	linkType := c.Param("link_type")
	id := c.Param("id")

	var properties map[string]interface{}
	if err := c.ShouldBindJSON(&properties); err != nil {
		Error(c, http.StatusBadRequest, "invalid request body")
		return
	}

	if err := h.linkService.UpdateLink(linkType, id, properties); err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, nil)
}

// DeleteLink 删除关系
func (h *LinkHandler) DeleteLink(c *gin.Context) {
	linkType := c.Param("link_type")
	id := c.Param("id")

	if err := h.linkService.DeleteLink(linkType, id); err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}

	Success(c, nil)
}

// ListLinks 列出所有关系
func (h *LinkHandler) ListLinks(c *gin.Context) {
	linkType := c.Param("link_type")

	offset, _ := strconv.Atoi(c.DefaultQuery("offset", "0"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))

	links, total, err := h.linkService.ListLinks(linkType, offset, limit)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, map[string]interface{}{
		"items": links,
		"total": total,
		"offset": offset,
		"limit": limit,
	})
}

// GetInstanceLinks 查询实例的关系
func (h *LinkHandler) GetInstanceLinks(c *gin.Context) {
	instanceID := c.Param("id")
	linkType := c.Param("link_type")

	links, err := h.linkService.GetLinksBySource(linkType, instanceID)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, links)
}

// GetConnectedInstances 查询关联的实例
func (h *LinkHandler) GetConnectedInstances(c *gin.Context) {
	objectType := c.Param("object_type")
	instanceID := c.Param("id")
	linkType := c.Param("link_type")
	direction := c.DefaultQuery("direction", "outgoing")

	instances, err := h.linkService.GetConnectedInstances(objectType, linkType, instanceID, direction)
	if err != nil {
		Error(c, http.StatusBadRequest, err.Error())
		return
	}

	Success(c, instances)
}

