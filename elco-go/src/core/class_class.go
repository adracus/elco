package core

type ClassClass struct {
	name          string
	class         *Type
	super         BaseClass
	props         *Properties
	instanceProps *Properties
}

func (c ClassClass) Super() BaseClass {
	return c.super
}

func (c ClassClass) Name() string {
	return c.name
}

func (c ClassClass) Class() *Type {
	return c.class
}

func (c ClassClass) Props() *Properties {
	return c.props
}

func (c ClassClass) InstanceProps() *Properties {
	return c.instanceProps
}

var Class *ClassClass

func init() {
	Class = &ClassClass{
		name:          "Class",
		props:         nil,
		instanceProps: nil,
	}

	Class.Props().SetAll("public", map[string]BaseInstance{
		"create": NewMethodInstance(func(name string) *UserClass {
			return &UserClass{
				name:          name,
				class:         SimpleType(Class),
				super:         Class,
				props:         Class.Props().Inheritable(),
				instanceProps: NewProperties(),
			}
		}),
	})
}
