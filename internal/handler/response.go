package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

// Response 统一响应格式
type Response struct {
	Code      int         `json:"code"`
	Message   string      `json:"message"`
	Data      interface{} `json:"data,omitempty"`
	Errors    []ErrorItem `json:"errors,omitempty"`
	Timestamp string      `json:"timestamp"`
}

// ErrorItem 错误项
type ErrorItem struct {
	Field   string `json:"field,omitempty"`
	Message string `json:"message"`
}

// Success 成功响应
func Success(c *gin.Context, data interface{}) {
	c.JSON(http.StatusOK, Response{
		Code:      http.StatusOK,
		Message:   "success",
		Data:      data,
		Timestamp: time.Now().Format(time.RFC3339),
	})
}

// Error 错误响应
func Error(c *gin.Context, code int, message string) {
	c.JSON(code, Response{
		Code:      code,
		Message:   message,
		Timestamp: time.Now().Format(time.RFC3339),
	})
}

// ValidationError 验证错误响应
func ValidationError(c *gin.Context, errors []ErrorItem) {
	c.JSON(http.StatusBadRequest, Response{
		Code:      http.StatusBadRequest,
		Message:   "validation failed",
		Errors:    errors,
		Timestamp: time.Now().Format(time.RFC3339),
	})
}

