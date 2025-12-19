package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"mypalantir/internal/service"
)

// SchemaHandler Schema 处理器
type SchemaHandler struct {
	schemaService *service.SchemaService
}

// NewSchemaHandler 创建 Schema 处理器
func NewSchemaHandler(schemaService *service.SchemaService) *SchemaHandler {
	return &SchemaHandler{
		schemaService: schemaService,
	}
}

// ListObjectTypes 列出所有对象类型
func (h *SchemaHandler) ListObjectTypes(c *gin.Context) {
	objectTypes := h.schemaService.ListObjectTypes()
	Success(c, objectTypes)
}

// GetObjectType 获取对象类型详情
func (h *SchemaHandler) GetObjectType(c *gin.Context) {
	name := c.Param("name")
	objectType, err := h.schemaService.GetObjectType(name)
	if err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}
	Success(c, objectType)
}

// GetObjectTypeProperties 获取对象类型的所有属性
func (h *SchemaHandler) GetObjectTypeProperties(c *gin.Context) {
	name := c.Param("name")
	properties, err := h.schemaService.GetObjectTypeProperties(name)
	if err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}
	Success(c, properties)
}

// ListLinkTypes 列出所有关系类型
func (h *SchemaHandler) ListLinkTypes(c *gin.Context) {
	linkTypes := h.schemaService.ListLinkTypes()
	Success(c, linkTypes)
}

// GetLinkType 获取关系类型详情
func (h *SchemaHandler) GetLinkType(c *gin.Context) {
	name := c.Param("name")
	linkType, err := h.schemaService.GetLinkType(name)
	if err != nil {
		Error(c, http.StatusNotFound, err.Error())
		return
	}
	Success(c, linkType)
}

// GetOutgoingLinks 获取对象的出边关系
func (h *SchemaHandler) GetOutgoingLinks(c *gin.Context) {
	name := c.Param("name")
	links := h.schemaService.GetOutgoingLinks(name)
	Success(c, links)
}

// GetIncomingLinks 获取对象的入边关系
func (h *SchemaHandler) GetIncomingLinks(c *gin.Context) {
	name := c.Param("name")
	links := h.schemaService.GetIncomingLinks(name)
	Success(c, links)
}

