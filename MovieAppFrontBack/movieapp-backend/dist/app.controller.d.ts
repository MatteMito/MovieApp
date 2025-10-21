export declare class AppController {
    getRoot(): {
        message: string;
        version: string;
        timestamp: string;
        endpoints: {
            api: string;
            health: string;
            enrich: string;
            analytics: string;
            lists: string;
            auth: string;
        };
        database: string;
        cache: string;
        websocket: string;
        status: string;
    };
    getApiInfo(): {
        message: string;
        version: string;
        timestamp: string;
        availableEndpoints: {
            movies: {
                health: string;
                enrich: string;
                batch: string;
                all: string;
                search: string;
                initialize: string;
            };
            analytics: {
                generate: string;
                cached: string;
                charts: string;
                quickStats: string;
            };
            lists: {
                create: string;
                getUserLists: string;
                getById: string;
                update: string;
                delete: string;
                addMovie: string;
                removeMovie: string;
                public: string;
                follow: string;
            };
            auth: {
                register: string;
                login: string;
                profile: string;
                validate: string;
            };
        };
        documentation: string;
        database: {
            type: string;
            orm: string;
            entities: string[];
            status: string;
        };
        cache: {
            tmdb: string;
            analytics: string;
            performance: string;
        };
        websocket: {
            namespace: string;
            features: string[];
        };
    };
}
