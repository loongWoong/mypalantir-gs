package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"

	"github.com/gin-gonic/gin"
	"mypalantir/internal/config"
	"mypalantir/internal/dsl"
	"mypalantir/internal/handler"
	"mypalantir/internal/middleware"
	"mypalantir/internal/service"
	"mypalantir/internal/storage"
)

func main() {
	// 加载配置
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 设置 Gin 模式
	gin.SetMode(cfg.Server.Mode)

	// 加载 DSL Schema
	loader := dsl.NewLoader(cfg.DSL.FilePath)
	if err := loader.Load(); err != nil {
		log.Fatalf("Failed to load DSL schema: %v", err)
	}

	schema := loader.GetSchema()
	if schema == nil {
		log.Fatal("Schema is nil after loading")
	}

	log.Printf("Schema loaded successfully: %d object types, %d link types",
		len(schema.ObjectTypes), len(schema.LinkTypes))

	// 初始化存储
	pathManager := storage.NewPathManager(cfg.Data.RootPath, schema.Namespace)
	instanceStorage := storage.NewInstanceStorage(pathManager)
	linkStorage := storage.NewLinkStorage(pathManager)

	// 初始化服务
	dataValidator := service.NewDataValidator(loader)
	schemaService := service.NewSchemaService(loader)
	instanceService := service.NewInstanceService(instanceStorage, loader, dataValidator)
	linkService := service.NewLinkService(linkStorage, instanceStorage, loader, dataValidator)

	// 初始化处理器
	schemaHandler := handler.NewSchemaHandler(schemaService)
	instanceHandler := handler.NewInstanceHandler(instanceService)
	linkHandler := handler.NewLinkHandler(linkService)

	// 创建路由
	router := gin.New()
	router.Use(middleware.Logger())
	router.Use(middleware.Recovery())
	router.Use(middleware.CORS())

	// API 路由
	api := router.Group("/api/v1")
	{
		// Schema 查询 API
		schemaAPI := api.Group("/schema")
		{
			schemaAPI.GET("/object-types", schemaHandler.ListObjectTypes)
			schemaAPI.GET("/object-types/:name", schemaHandler.GetObjectType)
			schemaAPI.GET("/object-types/:name/properties", schemaHandler.GetObjectTypeProperties)
			schemaAPI.GET("/object-types/:name/outgoing-links", schemaHandler.GetOutgoingLinks)
			schemaAPI.GET("/object-types/:name/incoming-links", schemaHandler.GetIncomingLinks)
			schemaAPI.GET("/link-types", schemaHandler.ListLinkTypes)
			schemaAPI.GET("/link-types/:name", schemaHandler.GetLinkType)
		}

		// 实例 CRUD API
		instancesAPI := api.Group("/instances")
		{
			instancesAPI.POST("/:object_type", instanceHandler.CreateInstance)
			instancesAPI.GET("/:object_type", instanceHandler.ListInstances)
			instancesAPI.GET("/:object_type/:id", instanceHandler.GetInstance)
			instancesAPI.PUT("/:object_type/:id", instanceHandler.UpdateInstance)
			instancesAPI.DELETE("/:object_type/:id", instanceHandler.DeleteInstance)
		}

		// 关系 CRUD API
		linksAPI := api.Group("/links")
		{
			linksAPI.POST("/:link_type", linkHandler.CreateLink)
			linksAPI.GET("/:link_type", linkHandler.ListLinks)
			linksAPI.GET("/:link_type/:id", linkHandler.GetLink)
			linksAPI.PUT("/:link_type/:id", linkHandler.UpdateLink)
			linksAPI.DELETE("/:link_type/:id", linkHandler.DeleteLink)
		}

		// 实例关系查询 API
		instancesAPI.GET("/:object_type/:id/links/:link_type", linkHandler.GetInstanceLinks)
		instancesAPI.GET("/:object_type/:id/connected/:link_type", linkHandler.GetConnectedInstances)
	}

	// 健康检查
	router.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{
			"status": "ok",
		})
	})

	// 静态文件服务（前端）
	staticPath := cfg.Frontend.StaticPath
	absPath, err := filepath.Abs(staticPath)
	if err == nil {
		staticPath = absPath
	}

	// 检查静态文件目录是否存在
	if _, err := os.Stat(filepath.Join(staticPath, "index.html")); err == nil {
		// 提供静态文件
		router.Static("/assets", filepath.Join(staticPath, "assets"))
		
		// 尝试提供 favicon
		faviconPath := filepath.Join(staticPath, "favicon.ico")
		if _, err := os.Stat(faviconPath); err == nil {
			router.StaticFile("/favicon.ico", faviconPath)
		}
		
		// SPA 路由：所有非 API 请求返回 index.html
		router.NoRoute(func(c *gin.Context) {
			// 如果是 API 请求，返回 404
			if len(c.Request.URL.Path) >= 4 && c.Request.URL.Path[:4] == "/api" {
				c.JSON(404, gin.H{"error": "Not found"})
				return
			}
			// 否则返回前端页面
			c.File(filepath.Join(staticPath, "index.html"))
		})
		
		log.Printf("Frontend static files served from: %s", staticPath)
	} else {
		log.Printf("Warning: Frontend static path not found: %s", staticPath)
		log.Printf("Frontend will not be served. API is available at /api/v1")
	}

	// 启动服务器
	addr := fmt.Sprintf(":%s", cfg.Server.Port)
	log.Printf("Server starting on %s", addr)
	log.Printf("API available at http://localhost%s/api/v1", addr)
	log.Printf("Frontend available at http://localhost%s", addr)
	if err := router.Run(addr); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}

